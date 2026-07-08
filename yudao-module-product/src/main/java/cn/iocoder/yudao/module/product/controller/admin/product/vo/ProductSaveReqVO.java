package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 产品创建/修改 Request VO")
@Data
public class ProductSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "产品名称不能为空")
    private String name;

    @Schema(description = "产品类型：1企业 2个人 3核验 4司法 5经营风险 6知识产权 7报告 8组合", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "产品类型不能为空")
    private Integer productType;

    @Schema(description = "认证类型")
    private Integer authType;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "版本")
    private String version;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "是否缓存")
    private Boolean cacheEnabled;

    @Schema(description = "是否支持异步")
    private Boolean asyncSupport;

    @Schema(description = "是否需要授权书编号")
    private Boolean needAuthNo;

    @Schema(description = "备注")
    private String remark;
}
