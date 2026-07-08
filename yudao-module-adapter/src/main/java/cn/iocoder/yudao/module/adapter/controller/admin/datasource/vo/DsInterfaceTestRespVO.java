package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Map;

@Schema(description = "管理后台 - 数据源接口联调测试台 Response VO")
@Data
public class DsInterfaceTestRespVO {

    @Schema(description = "实际请求方式")
    private String requestMethod;

    @Schema(description = "实际请求URL（渲染后）")
    private String requestUrl;

    @Schema(description = "实际请求头")
    private Map<String, String> requestHeaders;

    @Schema(description = "实际请求体")
    private String requestBody;

    @Schema(description = "上游响应状态码")
    private int statusCode;

    @Schema(description = "上游原始响应体")
    private String rawResponseBody;

    @Schema(description = "出参映射结果（平台字段名 -> 值）")
    private Map<String, Object> mappedData;

    @Schema(description = "平台应答码")
    private String platformCode;

    @Schema(description = "是否业务成功")
    private boolean success;

    @Schema(description = "是否计费")
    private boolean charge;

    @Schema(description = "是否可重试")
    private boolean retryable;

    @Schema(description = "是否触发熔断/切换")
    private boolean triggerSwitch;

    @Schema(description = "原始码是否命中映射")
    private boolean codeMapped;
}
