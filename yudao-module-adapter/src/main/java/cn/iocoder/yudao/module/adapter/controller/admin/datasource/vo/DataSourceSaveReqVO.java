package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 数据源创建/修改 Request VO")
@Data
public class DataSourceSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "数据源名称不能为空")
    private String name;

    @Schema(description = "供应商名称")
    private String supplierName;

    @Schema(description = "类型：1供应商 2内部", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "类型不能为空")
    private Integer sourceType;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系方式")
    private String contactPhone;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "环境：1生产 2测试", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "环境不能为空")
    private Integer envType;

    @Schema(description = "服务地址")
    private String serviceAddr;

    @Schema(description = "认证方式")
    private Integer authType;

    @Schema(description = "超时毫秒")
    private Integer timeoutMs;

    @Schema(description = "最大并发")
    private Integer maxConcurrency;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "是否参与路由")
    private Boolean routable;

    @Schema(description = "接入协议：1HTTP 2RPC 3DB 4FILE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "接入协议不能为空")
    private Integer protocolType;

    @Schema(description = "协议扩展参数JSON")
    private String protocolExtConfig;

    @Schema(description = "备注")
    private String remark;
}
