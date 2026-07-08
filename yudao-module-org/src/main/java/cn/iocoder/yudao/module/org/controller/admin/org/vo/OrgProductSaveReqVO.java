package cn.iocoder.yudao.module.org.controller.admin.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 机构产品开通创建/修改 Request VO")
@Data
public class OrgProductSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "所属机构ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属机构不能为空")
    private Long orgId;

    @Schema(description = "产品ID（松耦合引用 product）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "产品不能为空")
    private Long productId;

    @Schema(description = "产品编码快照", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "产品编码不能为空")
    private String productCode;

    @Schema(description = "状态：0开通 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "生效时间(空为立即)")
    private LocalDateTime effectiveTime;

    @Schema(description = "失效时间(空为长期)")
    private LocalDateTime expireTime;

    @Schema(description = "开通单价")
    private BigDecimal unitPrice;

    @Schema(description = "计费模板ID(松耦合,计费里程碑)")
    private Long billingTemplateId;

    @Schema(description = "日调用量上限")
    private Integer dailyLimit;

    @Schema(description = "月调用量上限")
    private Integer monthlyLimit;

    @Schema(description = "最大并发")
    private Integer concurrencyLimit;

    @Schema(description = "备注")
    private String remark;
}
