package cn.iocoder.yudao.module.org.dal.dataobject.org;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("org_info")
@KeySequence("org_info_seq")
@Data
@TenantIgnore
public class OrgDO extends BaseDO {
    private Long id;
    private String orgCode;
    private String name;
    private String unifiedSocialCreditCode;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private String businessOwner;
    private String remark;
}
