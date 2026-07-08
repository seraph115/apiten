package cn.iocoder.yudao.module.adapter.engine;

import lombok.Data;
import java.util.Map;

/**
 * HTTP 调用结果——供后续应答码解析/出参抽取使用
 */
@Data
public class HttpCallResult {
    private int statusCode;
    private String rawResponseBody;
    private String requestMethod;
    private String requestUrl;
    private Map<String, String> requestHeaders;
    private String requestBody;
}
