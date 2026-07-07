package cn.iocoder.yudao.module.adapter.dal.dataobject.datasource;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("adapter_data_source")
@KeySequence("adapter_data_source_seq")
@Data
@TenantIgnore
public class DataSourceDO extends BaseDO {
    private Long id;
    private String dsCode;
    private String name;
    private String supplierName;
    private Integer sourceType;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private Integer envType;
    private String serviceAddr;
    private Integer authType;
    private Integer timeoutMs;
    private Integer maxConcurrency;
    private Integer retryCount;
    private Boolean routable;
    private Integer protocolType;
    private String protocolExtConfig;
    private String remark;
}
