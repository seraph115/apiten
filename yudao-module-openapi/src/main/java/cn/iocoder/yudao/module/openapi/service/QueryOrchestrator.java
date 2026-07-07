package cn.iocoder.yudao.module.openapi.service;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.apiten.common.api.ApiResponse;
import cn.apiten.common.api.PlatformErrorCode;
import cn.apiten.common.flow.FlowEvent;
import cn.apiten.common.id.SnowflakeIdGenerator;
import cn.iocoder.yudao.module.openapi.client.AdapterClient;
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
    private final ObjectMapper mapper = new ObjectMapper();

    public QueryOrchestrator(ObjectProvider<AdapterClient> adapterClientProvider,
            ObjectProvider<KafkaTemplate<String, String>> kafkaProvider) {
        this.adapterClientProvider = adapterClientProvider;
        this.kafkaProvider = kafkaProvider;
    }

    public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        AdapterClient client = adapterClientProvider.getIfAvailable();
        ApiResponse<Map<String, Object>> resp;
        if (client == null) { // 单测/降级分支
            Map<String, Object> data = Map.of("mock", true, "echo", params);
            resp = ApiResponse.of(idGen.nextIdStr(), productCode,
                    PlatformErrorCode.SUCCESS, true, System.currentTimeMillis() - start, data);
        } else {
            ProviderRequest req = new ProviderRequest();
            req.setProductCode(productCode);
            req.setParams(params);
            ProviderResponse providerResp = client.invoke(req);
            PlatformErrorCode ec = "0000".equals(providerResp.getPlatformCode())
                    ? PlatformErrorCode.SUCCESS : PlatformErrorCode.UPSTREAM_ERROR;
            resp = ApiResponse.of(idGen.nextIdStr(), productCode, ec,
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
