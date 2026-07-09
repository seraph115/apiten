package cn.iocoder.yudao.module.openapi.service;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.apiten.common.api.ApiResponse;
import cn.apiten.common.api.PlatformErrorCode;
import cn.apiten.common.flow.FlowEvent;
import cn.apiten.common.id.SnowflakeIdGenerator;
import cn.apiten.common.route.RouteResolveRespDTO;
import cn.iocoder.yudao.module.openapi.client.AdapterClient;
import cn.iocoder.yudao.module.openapi.client.RouteClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class QueryOrchestrator {

    private final SnowflakeIdGenerator idGen = new SnowflakeIdGenerator(1);
    private final ObjectProvider<AdapterClient> adapterClientProvider;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaProvider;
    private final ObjectProvider<RouteClient> routeClientProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    public QueryOrchestrator(ObjectProvider<AdapterClient> adapterClientProvider,
            ObjectProvider<KafkaTemplate<String, String>> kafkaProvider,
            ObjectProvider<RouteClient> routeClientProvider) {
        this.adapterClientProvider = adapterClientProvider;
        this.kafkaProvider = kafkaProvider;
        this.routeClientProvider = routeClientProvider;
    }

    public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params) {
        return query(productCode, params, null, null);
    }

    public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params, String flowNo) {
        return query(productCode, params, flowNo, null);
    }

    public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params, String flowNo,
            Long orgId) {
        long start = System.currentTimeMillis();
        String usedFlowNo = (flowNo != null && !flowNo.isBlank()) ? flowNo : idGen.nextIdStr();
        AdapterClient client = adapterClientProvider.getIfAvailable();
        ApiResponse<Map<String, Object>> resp;
        if (client == null) { // 单测/降级：保持 P0 mock 分支
            Map<String, Object> data = Map.of("mock", true, "echo", params);
            resp = ApiResponse.of(usedFlowNo, productCode,
                    PlatformErrorCode.SUCCESS, true, System.currentTimeMillis() - start, data);
        } else {
            Long dsInterfaceId = null;
            RouteClient routeClient = routeClientProvider.getIfAvailable();
            if (routeClient != null) {
                RouteResolveRespDTO route = routeClient.resolve(productCode, orgId);
                if (route == null || route.getDsInterfaceId() == null) {
                    return ApiResponse.of(usedFlowNo, productCode, PlatformErrorCode.ROUTE_NO_TARGET,
                            false, System.currentTimeMillis() - start, null);
                }
                dsInterfaceId = route.getDsInterfaceId();
            }
            ProviderRequest req = new ProviderRequest();
            req.setProductCode(productCode);
            req.setParams(params);
            req.setDsInterfaceId(dsInterfaceId); // 非空 → adapter 走 P2 HTTP 引擎；空 → mock 兜底
            ProviderResponse providerResp = client.invoke(req);
            PlatformErrorCode ec = "0000".equals(providerResp.getPlatformCode())
                    ? PlatformErrorCode.SUCCESS : PlatformErrorCode.UPSTREAM_ERROR;
            resp = ApiResponse.of(usedFlowNo, productCode, ec,
                    ec == PlatformErrorCode.SUCCESS, System.currentTimeMillis() - start, providerResp.getData());
        }
        sendFlowEvent(resp, productCode, start);
        return resp;
    }

    private void sendFlowEvent(ApiResponse<Map<String, Object>> resp, String productCode, long start) {
        KafkaTemplate<String, String> kafka = kafkaProvider.getIfAvailable();
        if (kafka == null) {
            return;
        }
        FlowEvent e = new FlowEvent();
        e.setFlowNo(resp.getFlowNo());
        e.setProductCode(productCode);
        e.setPlatformCode(resp.getCode());
        e.setCharged(resp.isCharged());
        e.setCostTimeMs(resp.getCostTime());
        e.setRequestTimeEpochMs(start);
        try {
            kafka.send("apiten.org-flow", mapper.writeValueAsString(e));
        } catch (Exception ignore) {
            // P0 尽力而为；P6 引入本地兜底与重试
        }
    }
}
