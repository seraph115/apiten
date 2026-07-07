package cn.iocoder.yudao.module.adapter.dal.dataobject.datasource;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("adapter_ds_interface_param")
@KeySequence("adapter_ds_interface_param_seq")
@Data
@TenantIgnore
public class DsInterfaceParamDO extends BaseDO {
    private Long id;
    private Long dsInterfaceId;
    private Integer paramDirection;
    private String platformField;
    private String providerField;
    private Integer dataType;
    private Boolean required;
    private String transformFn;
    private String defaultValue;
    private String jsonPath;
    private String remark;
}
