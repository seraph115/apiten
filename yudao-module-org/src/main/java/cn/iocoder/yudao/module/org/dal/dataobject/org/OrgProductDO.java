package cn.iocoder.yudao.module.org.dal.dataobject.org;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("org_product")
@KeySequence("org_product_seq")
@Data
@TenantIgnore
public class OrgProductDO extends BaseDO {
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
}
