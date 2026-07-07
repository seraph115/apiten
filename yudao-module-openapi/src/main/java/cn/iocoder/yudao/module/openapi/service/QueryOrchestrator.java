package cn.iocoder.yudao.module.openapi.service;

import cn.apiten.common.api.ApiResponse;
import cn.apiten.common.api.PlatformErrorCode;
import cn.apiten.common.id.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class QueryOrchestrator {

    private final SnowflakeIdGenerator idGen = new SnowflakeIdGenerator(1);

    public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = Map.of("mock", true, "echo", params);
        return ApiResponse.of(idGen.nextIdStr(), productCode,
                PlatformErrorCode.SUCCESS, true, System.currentTimeMillis() - start, data);
    }
}
