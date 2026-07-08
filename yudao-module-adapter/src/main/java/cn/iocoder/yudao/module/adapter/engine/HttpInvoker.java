package cn.iocoder.yudao.module.adapter.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

/**
 * HTTP 执行器——基于 Spring {@link RestClient} 发起外调请求。
 *
 * <p>4xx/5xx 不抛异常（交由上层应答码解析），对 IO 异常或 5xx 响应最多重试 {@code retryCount} 次。</p>
 *
 * <p>注意：{@code client} 在构造器内由注入的 {@link RestClient.Builder} 一次性 build，
 * 调用过程中不再 clone/覆盖 requestFactory —— 这是为了兼容测试用
 * {@link org.springframework.test.web.client.MockRestServiceServer#bindTo(RestClient.Builder)}：
 * mock 只在“注入的 builder 构建出的 client”上生效，若在 call() 内重新 build 或覆盖
 * requestFactory 会解绑 mock。按接口 timeoutMs 动态设置超时因此本期未实现，
 * 超时兜底值由 config 层的 RestClient.Builder 统一预置。</p>
 */
@Component
@Slf4j
public class HttpInvoker {

    private final RestClient client;

    public HttpInvoker(RestClient.Builder adapterRestClientBuilder) {
        this.client = adapterRestClientBuilder.build();
    }

    public HttpCallResult call(String method, String url, Map<String, String> headers,
            String body, int timeoutMs, int retryCount) {
        HttpCallResult result = new HttpCallResult();
        result.setRequestMethod(method);
        result.setRequestUrl(url);
        result.setRequestHeaders(headers == null ? Map.of() : headers);
        result.setRequestBody(body);

        int attempts = Math.max(0, retryCount) + 1;
        RuntimeException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                RestClient.RequestBodySpec spec =
                        client.method(HttpMethod.valueOf(method.toUpperCase())).uri(url);
                if (headers != null) {
                    headers.forEach(spec::header);
                }
                if (body != null) {
                    spec.body(body);
                }
                var resp = spec.retrieve()
                        .onStatus(s -> true, (req, res) -> { }) // 4xx/5xx 不抛
                        .toEntity(String.class);
                result.setStatusCode(resp.getStatusCode().value());
                result.setRawResponseBody(resp.getBody());
                if (resp.getStatusCode().is5xxServerError() && i < attempts - 1) {
                    continue; // 5xx 重试
                }
                return result;
            } catch (RuntimeException e) { // IO/连接异常
                last = e;
                log.warn("[http] 调用失败 attempt {}/{} url={} err={}", i + 1, attempts, url, e.getMessage());
            }
        }
        throw last != null ? last : new IllegalStateException("HTTP 调用失败: " + url);
    }
}
