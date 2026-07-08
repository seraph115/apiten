package cn.iocoder.yudao.module.org.controller.admin.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 机构产品开通 Response VO")
@Data
public class OrgProductRespVO {
    private Long id;
    private Long orgId;
    private Long productId;
    private String productCode;
    private Integer status;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private BigDecimal unitPrice;
    private Long billingTemplateId;
    private Integer dailyLimit;
    private Integer monthlyLimit;
    private Integer concurrencyLimit;
    private String remark;
    private LocalDateTime createTime;
}
