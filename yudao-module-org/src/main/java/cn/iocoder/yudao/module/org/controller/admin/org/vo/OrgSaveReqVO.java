package cn.iocoder.yudao.module.org.controller.admin.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 机构创建/修改 Request VO")
@Data
public class OrgSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "机构名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "机构名称不能为空")
    private String name;

    @Schema(description = "统一社会信用代码")
    private String unifiedSocialCreditCode;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系方式")
    private String contactPhone;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "业务归属")
    private String businessOwner;

    @Schema(description = "备注")
    private String remark;
}
