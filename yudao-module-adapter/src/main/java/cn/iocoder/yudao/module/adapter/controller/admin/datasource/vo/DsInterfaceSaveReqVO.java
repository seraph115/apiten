package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 数据源接口创建/修改 Request VO")
@Data
public class DsInterfaceSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "接口名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "接口名称不能为空")
    private String name;

    @Schema(description = "所属数据源ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属数据源不能为空")
    private Long dataSourceId;

    @Schema(description = "接口URI")
    private String uri;

    @Schema(description = "请求方式", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请求方式不能为空")
    private String method;

    @Schema(description = "报文格式：1JSON 2XML 3FORM", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "报文格式不能为空")
    private Integer msgFormat;

    @Schema(description = "签名方式")
    private Integer signType;

    @Schema(description = "加密方式")
    private Integer encryptType;

    @Schema(description = "认证参数JSON")
    private String authParams;

    @Schema(description = "接口版本")
    private String version;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "超时毫秒")
    private Integer timeoutMs;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "是否缓存")
    private Boolean cacheEnabled;

    @Schema(description = "缓存TTL秒")
    private Integer cacheTtl;

    @Schema(description = "缓存键模板")
    private String cacheKey;

    @Schema(description = "备注")
    private String remark;
}
