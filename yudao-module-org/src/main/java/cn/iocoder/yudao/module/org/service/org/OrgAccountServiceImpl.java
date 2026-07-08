package cn.iocoder.yudao.module.org.service.org;

import cn.apiten.common.crypto.AesCipher;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgAccountMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;

import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_ACCOUNT_NOT_EXISTS;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_NOT_EXISTS;

@Service
@Validated
public class OrgAccountServiceImpl implements OrgAccountService {

    @Value("${apiten.org.sk-secret:apiten-default-dev-key}")
    private String skSecret;

    @Resource
    private OrgAccountMapper accountMapper;
    @Resource
    private OrgMapper orgMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAccount(OrgAccountSaveReqVO reqVO) {
        validateOrgExists(reqVO.getOrgId());
        OrgAccountDO account = BeanUtils.toBean(reqVO, OrgAccountDO.class);
        account.setId(null);
        account.setAppKey(generateAppKey());
        account.setSecretKeyCipher(AesCipher.encrypt(generateSecret(), skSecret));
        account.setKeyStatus(0);
        if (account.getSignAlgorithm() == null) {
            account.setSignAlgorithm("HMAC-SHA256");
        }
        accountMapper.insert(account);
        return account.getId();
    }

    @Override
    public void updateAccount(OrgAccountSaveReqVO reqVO) {
        validateAccountExists(reqVO.getId());
        OrgAccountDO account = BeanUtils.toBean(reqVO, OrgAccountDO.class);
        account.setAppKey(null);
        account.setSecretKeyCipher(null);
        accountMapper.updateById(account);
    }

    @Override
    public void deleteAccount(Long id) {
        validateAccountExists(id);
        accountMapper.deleteById(id);
    }

    @Override
    public OrgAccountDO getAccount(Long id) {
        return accountMapper.selectById(id);
    }

    @Override
    public OrgAccountDO getAccountByAppKey(String appKey) {
        return accountMapper.selectByAppKey(appKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetSecret(Long id) {
        if (accountMapper.selectById(id) == null) {
            throw exception(ORG_ACCOUNT_NOT_EXISTS);
        }
        String skPlain = generateSecret();
        OrgAccountDO upd = new OrgAccountDO();
        upd.setId(id);
        upd.setSecretKeyCipher(AesCipher.encrypt(skPlain, skSecret));
        upd.setKeyStatus(0);
        accountMapper.updateById(upd);
        return skPlain;
    }

    @Override
    public PageResult<OrgAccountDO> getAccountPage(OrgAccountPageReqVO reqVO) {
        return accountMapper.selectPage(reqVO);
    }

    @Override
    public List<OrgAccountDO> getListByOrgId(Long orgId) {
        return accountMapper.selectListByOrgId(orgId);
    }

    private void validateOrgExists(Long orgId) {
        if (orgMapper.selectById(orgId) == null) {
            throw exception(ORG_NOT_EXISTS);
        }
    }

    private void validateAccountExists(Long id) {
        if (accountMapper.selectById(id) == null) {
            throw exception(ORG_ACCOUNT_NOT_EXISTS);
        }
    }

    private String generateAppKey() {
        return "AK" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateSecret() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }
}
