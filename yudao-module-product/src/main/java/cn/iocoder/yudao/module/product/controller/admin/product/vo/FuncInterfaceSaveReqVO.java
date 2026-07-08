package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 功能接口绑定创建/修改 Request VO")
@Data
public class FuncInterfaceSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "所属产品功能ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属产品功能不能为空")
    private Long productFunctionId;

    @Schema(description = "数据源接口ID（松耦合引用 adapter）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据源接口不能为空")
    private Long dsInterfaceId;

    @Schema(description = "数据源接口编码快照")
    private String dsInterfaceCode;

    @Schema(description = "优先级（越小越优先）")
    private Integer priority;

    @Schema(description = "是否默认数据源")
    private Boolean isDefault;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;
}
