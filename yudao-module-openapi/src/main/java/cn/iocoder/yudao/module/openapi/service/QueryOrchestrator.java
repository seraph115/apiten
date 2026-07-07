package cn.iocoder.yudao.module.openapi.service;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.apiten.common.api.ApiResponse;
import cn.apiten.common.api.PlatformErrorCode;
import cn.apiten.common.id.SnowflakeIdGenerator;
import cn.iocoder.yudao.module.openapi.client.AdapterClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class QueryOrchestrator {

    private final SnowflakeIdGenerator idGen = new SnowflakeIdGenerator(1);
    private final ObjectProvider<AdapterClient> adapterClientProvider;

    public QueryOrchestrator(ObjectProvider<AdapterClient> adapterClientProvider) {
        this.adapterClientProvider = adapterClientProvider;
    }

    public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        AdapterClient client = adapterClientProvider.getIfAvailable();
        if (client == null) { // 单测/降级分支
            Map<String, Object> data = Map.of("mock", true, "echo", params);
            return ApiResponse.of(idGen.nextIdStr(), productCode,
                    PlatformErrorCode.SUCCESS, true, System.currentTimeMillis() - start, data);
        }
        ProviderRequest req = new ProviderRequest();
        req.setProductCode(productCode);
        req.setParams(params);
        ProviderResponse resp = client.invoke(req);
        PlatformErrorCode ec = "0000".equals(resp.getPlatformCode())
                ? PlatformErrorCode.SUCCESS : PlatformErrorCode.UPSTREAM_ERROR;
        return ApiResponse.of(idGen.nextIdStr(), productCode, ec,
                ec == PlatformErrorCode.SUCCESS, System.currentTimeMillis() - start, resp.getData());
    }
}
