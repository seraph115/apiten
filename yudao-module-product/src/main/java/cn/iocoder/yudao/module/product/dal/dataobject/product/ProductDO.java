package cn.iocoder.yudao.module.product.dal.dataobject.product;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("product_info")
@KeySequence("product_info_seq")
@Data
@TenantIgnore
public class ProductDO extends BaseDO {
    private Long id;
    private String productCode;
    private String name;
    private Integer productType;
    private Integer authType;
    private Integer status;
    private String version;
    private String description;
    private Boolean cacheEnabled;
    private Boolean asyncSupport;
    private Boolean needAuthNo;
    private String remark;
}
