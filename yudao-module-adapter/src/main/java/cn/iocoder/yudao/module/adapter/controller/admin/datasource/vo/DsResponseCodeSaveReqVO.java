package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 数据源应答码创建/修改 Request VO")
@Data
public class DsResponseCodeSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "所属数据源ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属数据源不能为空")
    private Long dataSourceId;

    @Schema(description = "所属接口ID，为空表示数据源级通用")
    private Long dsInterfaceId;

    @Schema(description = "原始应答码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "原始应答码不能为空")
    private String rawCode;

    @Schema(description = "应答描述")
    private String rawDesc;

    @Schema(description = "是否成功", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否成功不能为空")
    private Boolean success;

    @Schema(description = "是否计费", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否计费不能为空")
    private Boolean charge;

    @Schema(description = "是否可重试", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否可重试不能为空")
    private Boolean retryable;

    @Schema(description = "是否触发切换", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否触发切换不能为空")
    private Boolean triggerSwitch;

    @Schema(description = "映射平台统一码")
    private String platformCode;
}
