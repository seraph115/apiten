package cn.iocoder.yudao.module.adapter.engine;

import lombok.Data;
import java.util.Map;

/**
 * 引擎编排结果——聚合 HTTP 外调、出参映射与应答码解析结果
 */
@Data
public class EngineResult {
    private HttpCallResult rawCall;
    private Map<String, Object> mappedData;
    private String platformCode;
    private boolean success;
    private boolean charge;
    private boolean retryable;
    private boolean triggerSwitch;
    private boolean codeMapped;
}
