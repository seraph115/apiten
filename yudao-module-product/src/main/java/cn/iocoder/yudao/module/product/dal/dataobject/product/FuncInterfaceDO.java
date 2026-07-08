package cn.iocoder.yudao.module.product.dal.dataobject.product;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("product_func_interface")
@KeySequence("product_func_interface_seq")
@Data
@TenantIgnore
public class FuncInterfaceDO extends BaseDO {
    private Long id;
    private Long productFunctionId;
    private Long dsInterfaceId;
    private String dsInterfaceCode;
    private Integer priority;
    private Boolean isDefault;
    private Integer status;
}
