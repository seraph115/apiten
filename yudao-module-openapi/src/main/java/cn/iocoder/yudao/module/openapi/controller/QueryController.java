package cn.iocoder.yudao.module.openapi.controller;

import cn.apiten.common.api.ApiResponse;
import cn.iocoder.yudao.module.openapi.service.QueryOrchestrator;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/openapi/v1")
public class QueryController {

    private final QueryOrchestrator orchestrator;

    public QueryController(QueryOrchestrator orchestrator) { this.orchestrator = orchestrator; }

    @PostMapping("/{productCode}/query")
    public ApiResponse<Map<String, Object>> query(@PathVariable String productCode,
            @RequestBody Map<String, Object> params) {
        return orchestrator.query(productCode, params);
    }
}
