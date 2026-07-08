package cn.iocoder.yudao.module.org.service.org;

import cn.apiten.common.crypto.AesCipher;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_ACCOUNT_NOT_EXISTS;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(OrgAccountServiceImpl.class)
@TestPropertySource(properties = "apiten.org.sk-secret=unit-test-key")
class OrgAccountServiceImplTest extends BaseDbUnitTest {

    @Resource private OrgAccountServiceImpl service;
    @Resource private OrgMapper orgMapper;

    private Long newOrg() {
        OrgDO o = new OrgDO();
        o.setOrgCode("ORG000001"); o.setName("机构"); o.setStatus(0);
        orgMapper.insert(o);
        return o.getId();
    }

    private OrgAccountSaveReqVO req(Long orgId) {
        OrgAccountSaveReqVO vo = new OrgAccountSaveReqVO();
        vo.setOrgId(orgId);
        vo.setAccountName("默认账号");
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesAkAndEncryptsSk() {
        Long orgId = newOrg();
        Long id = service.createAccount(req(orgId));
        OrgAccountDO db = service.getAccount(id);
        assertThat(db.getAppKey()).startsWith("AK").hasSizeGreaterThan(10);
        assertThat(db.getSecretKeyCipher()).isNotBlank();
        assertThat(AesCipher.decrypt(db.getSecretKeyCipher(), "unit-test-key")).isNotBlank();
    }

    @Test
    void create_parentOrgNotExists_throws() {
        assertServiceException(() -> service.createAccount(req(99999L)), ORG_NOT_EXISTS);
    }

    @Test
    void getByAppKey_returnsAccount() {
        Long orgId = newOrg();
        Long id = service.createAccount(req(orgId));
        String ak = service.getAccount(id).getAppKey();
        assertThat(service.getAccountByAppKey(ak).getId()).isEqualTo(id);
    }

    @Test
    void resetSecret_changesCipher_returnsNewPlain() {
        Long orgId = newOrg();
        Long id = service.createAccount(req(orgId));
        String oldCipher = service.getAccount(id).getSecretKeyCipher();
        String newPlain = service.resetSecret(id);
        assertThat(newPlain).isNotBlank();
        String newCipher = service.getAccount(id).getSecretKeyCipher();
        assertThat(newCipher).isNotEqualTo(oldCipher);
        assertThat(AesCipher.decrypt(newCipher, "unit-test-key")).isEqualTo(newPlain);
    }

    @Test
    void resetSecret_notExists_throws() {
        assertServiceException(() -> service.resetSecret(99999L), ORG_ACCOUNT_NOT_EXISTS);
    }

    @Test
    void listByOrgId_filters() {
        Long orgId = newOrg();
        service.createAccount(req(orgId));
        service.createAccount(req(orgId));
        assertThat(service.getListByOrgId(orgId)).hasSize(2);
    }
}
