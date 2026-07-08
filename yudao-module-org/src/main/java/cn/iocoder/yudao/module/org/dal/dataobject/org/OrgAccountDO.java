package cn.iocoder.yudao.module.org.dal.dataobject.org;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("org_account")
@KeySequence("org_account_seq")
@Data
@TenantIgnore
public class OrgAccountDO extends BaseDO {
    private Long id;
    private Long orgId;
    private String accountName;
    private String appKey;
    private String secretKeyCipher;
    private Integer keyStatus;
    private String ipWhitelist;
    private String callbackUrl;
    private String signAlgorithm;
    private LocalDateTime expireTime;
    private Integer concurrencyLimit;
    private Integer dailyLimit;
    private Integer monthlyLimit;
    private Integer status;
    private String remark;
}
