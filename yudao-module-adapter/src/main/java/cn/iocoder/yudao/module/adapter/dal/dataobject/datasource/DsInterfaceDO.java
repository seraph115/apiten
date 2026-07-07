package cn.iocoder.yudao.module.adapter.dal.dataobject.datasource;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("adapter_ds_interface")
@KeySequence("adapter_ds_interface_seq")
@Data
@TenantIgnore
public class DsInterfaceDO extends BaseDO {
    private Long id;
    private String ifCode;
    private String name;
    private Long dataSourceId;
    private String uri;
    private String method;
    private Integer msgFormat;
    private Integer signType;
    private Integer encryptType;
    private String authParams;
    private String version;
    private Integer status;
    private Integer timeoutMs;
    private Integer retryCount;
    private Boolean cacheEnabled;
    private Integer cacheTtl;
    private String cacheKey;
    private String remark;
}
