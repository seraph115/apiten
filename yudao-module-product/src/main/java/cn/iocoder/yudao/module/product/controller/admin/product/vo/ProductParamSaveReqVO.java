package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 产品参数创建/修改 Request VO")
@Data
public class ProductParamSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "所属产品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属产品不能为空")
    private Long productId;

    @Schema(description = "方向：1入参 2出参", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "方向不能为空")
    private Integer paramDirection;

    @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "字段名不能为空")
    private String fieldName;

    @Schema(description = "数据类型：1字符串 2数字 3布尔 4日期 5对象 6数组", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据类型不能为空")
    private Integer dataType;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "入参校验规则")
    private String validationRule;

    @Schema(description = "出参脱敏规则")
    private String desensitizeRule;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "排序")
    private Integer sort;
}
