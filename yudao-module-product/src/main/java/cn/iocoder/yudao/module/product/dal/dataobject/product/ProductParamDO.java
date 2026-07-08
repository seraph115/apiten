package cn.iocoder.yudao.module.product.dal.dataobject.product;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("product_param")
@KeySequence("product_param_seq")
@Data
@TenantIgnore
public class ProductParamDO extends BaseDO {
    private Long id;
    private Long productId;
    private Integer paramDirection;
    private String fieldName;
    private Integer dataType;
    private Boolean required;
    private String validationRule;
    private String desensitizeRule;
    private String description;
    private Integer sort;
}
