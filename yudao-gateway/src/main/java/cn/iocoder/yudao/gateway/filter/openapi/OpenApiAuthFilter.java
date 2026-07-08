package cn.iocoder.yudao.gateway.filter.openapi;

import cn.apiten.common.crypto.CryptoSignatures;
import cn.apiten.common.id.SnowflakeIdGenerator;
import cn.apiten.common.api.PlatformErrorCode;
import cn.apiten.common.org.OrgAuthVerifyReqDTO;
import cn.apiten.common.org.OrgAuthVerifyRespDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对外开放 API（/api/v1/**）五重鉴权过滤器
 * <p>
 * 1. 缓存请求体 -> SHA-256 摘要
 * 2. 生成 flowNo（雪花 ID）
 * 3. POST lb://org-server/rpc-api/org-auth/verify 校验签名/时间戳+nonce/IP/账号/机构/产品有效期
 * 4. 通过：注入 X-Org-Id/X-Account-Id/X-Org-Code/X-Product-Code/X-Flow-No 上下文头并转发（重放已缓存的请求体）
 * 5. 不通过：直接写回统一 ApiResponse 错误 JSON（HTTP 200）
 */
@Component
public class OpenApiAuthFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SnowflakeIdGenerator idGen;

    public OpenApiAuthFilter(ReactorLoadBalancerExchangeFilterFunction lbFunction,
            @Value("${apiten.gateway.worker-id:1}") long workerId) {
        this.webClient = WebClient.builder().filter(lbFunction).build();
        this.idGen = new SnowflakeIdGenerator(workerId);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!OpenApiPathUtils.isOpenApiPath(path)) {
            return chain.filter(exchange); // 非对外 API，放行
        }
        final String productCode = OpenApiPathUtils.extractProductCode(path);
        final String flowNo = idGen.nextIdStr();
        HttpHeaders headers = exchange.getRequest().getHeaders();
        final String appKey = headers.getFirst("X-App-Key");
        final String timestamp = headers.getFirst("X-Timestamp");
        final String nonce = headers.getFirst("X-Nonce");
        final String signature = headers.getFirst("X-Signature");
        String ip = exchange.getRequest().getRemoteAddress() == null ? null
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String xff = headers.getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            ip = xff.split(",")[0].trim();
        }
        final String clientIp = ip;

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String bodyDigest = CryptoSignatures.sha256Hex(new String(bytes, StandardCharsets.UTF_8));

                    OrgAuthVerifyReqDTO req = new OrgAuthVerifyReqDTO();
                    req.setAppKey(appKey);
                    req.setTimestamp(timestamp);
                    req.setNonce(nonce);
                    req.setSignature(signature);
                    req.setBodyDigest(bodyDigest);
                    req.setProductCode(productCode);
                    req.setClientIp(clientIp);
                    req.setFlowNo(flowNo);

                    return webClient.post()
                            .uri("lb://org-server/rpc-api/org-auth/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(req)
                            .retrieve()
                            .bodyToMono(OrgAuthVerifyRespDTO.class)
                            .onErrorResume(e -> Mono.just(OrgAuthVerifyRespDTO.fail(PlatformErrorCode.SYSTEM_ERROR)))
                            .flatMap(resp -> {
                                if (!resp.isPass()) {
                                    return writeError(exchange, flowNo, productCode, resp);
                                }
                                ServerHttpRequest mutated = new CachedBodyRequestDecorator(
                                        exchange.getRequest(), bytes, b -> {
                                            b.set("X-Org-Id", String.valueOf(resp.getOrgId()));
                                            b.set("X-Account-Id", String.valueOf(resp.getAccountId()));
                                            b.set("X-Org-Code", resp.getOrgCode());
                                            b.set("X-Product-Code", productCode);
                                            b.set("X-Flow-No", flowNo);
                                        });
                                return chain.filter(exchange.mutate().request(mutated).build());
                            });
                });
    }

    private Mono<Void> writeError(ServerWebExchange exchange, String flowNo, String productCode,
            OrgAuthVerifyRespDTO resp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flowNo", flowNo);
        body.put("productCode", productCode);
        body.put("code", resp.getPlatformCode());
        body.put("msg", resp.getMsg());
        body.put("charged", false);
        body.put("costTime", 0);
        body.put("data", null);
        byte[] out;
        try {
            out = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            out = "{\"code\":\"3999\",\"msg\":\"系统异常\"}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(out);
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return -50;
    }

    /** 用已读取字节重放请求体 + 覆写请求头 */
    static class CachedBodyRequestDecorator extends ServerHttpRequestDecorator {
        private final byte[] body;
        private final HttpHeaders headers;

        CachedBodyRequestDecorator(ServerHttpRequest delegate, byte[] body,
                java.util.function.Consumer<HttpHeaders> headerCustomizer) {
            super(delegate);
            this.body = body;
            HttpHeaders h = new HttpHeaders();
            h.addAll(delegate.getHeaders());
            headerCustomizer.accept(h);
            h.remove(HttpHeaders.TRANSFER_ENCODING);
            h.setContentLength(body.length);
            this.headers = h;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return Flux.defer(() -> Mono.just(new DefaultDataBufferFactory().wrap(body)));
        }
    }
}
