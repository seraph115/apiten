package cn.iocoder.yudao.module.adapter.dal.dataobject.datasource;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("adapter_ds_response_code")
@KeySequence("adapter_ds_response_code_seq")
@Data
@TenantIgnore
public class DsResponseCodeDO extends BaseDO {
    private Long id;
    private Long dataSourceId;
    private Long dsInterfaceId;
    private String rawCode;
    private String rawDesc;
    private Boolean success;
    private Boolean charge;
    private Boolean retryable;
    private Boolean triggerSwitch;
    private String platformCode;
}
