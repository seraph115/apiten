package cn.iocoder.yudao.module.product.dal.dataobject.product;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("product_function")
@KeySequence("product_function_seq")
@Data
@TenantIgnore
public class ProductFunctionDO extends BaseDO {
    private Long id;
    private String funcCode;
    private String name;
    private Long productId;
    private Integer sort;
    private Boolean required;
    private Boolean charge;
    private Integer status;
    private String remark;
}
