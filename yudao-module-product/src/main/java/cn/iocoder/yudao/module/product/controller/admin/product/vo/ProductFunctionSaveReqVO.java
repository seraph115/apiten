package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 产品功能创建/修改 Request VO")
@Data
public class ProductFunctionSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "功能名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "功能名称不能为空")
    private String name;

    @Schema(description = "所属产品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属产品不能为空")
    private Long productId;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "是否必选")
    private Boolean required;

    @Schema(description = "是否计费")
    private Boolean charge;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
