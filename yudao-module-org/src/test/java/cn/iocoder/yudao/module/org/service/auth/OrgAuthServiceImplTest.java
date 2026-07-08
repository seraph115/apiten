package cn.iocoder.yudao.module.org.service.auth;

import cn.apiten.common.crypto.AesCipher;
import cn.apiten.common.crypto.CryptoSignatures;
import cn.apiten.common.org.OrgAuthVerifyReqDTO;
import cn.apiten.common.org.OrgAuthVerifyRespDTO;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgAccountMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import({OrgAuthServiceImpl.class, InMemoryNonceStore.class})
@TestPropertySource(properties = {"apiten.org.sk-secret=unit-test-key", "apiten.org.timestamp-window-ms=300000"})
class OrgAuthServiceImplTest extends BaseDbUnitTest {

    private static final String SK = "sk-plaintext-secret";

    @Resource private OrgAuthServiceImpl service;
    @Resource private OrgMapper orgMapper;
    @Resource private OrgAccountMapper accountMapper;
    @Resource private OrgProductMapper orgProductMapper;

    /** 造：启用机构 + 启用账号(SK 已知) + 已开通产品；返回 appKey */
    private String seedHappyPath(String appKey, String ip) {
        OrgDO org = new OrgDO();
        org.setOrgCode("ORG000001"); org.setName("机构"); org.setStatus(0);
        orgMapper.insert(org);
        OrgAccountDO acc = new OrgAccountDO();
        acc.setOrgId(org.getId()); acc.setAppKey(appKey);
        acc.setSecretKeyCipher(AesCipher.encrypt(SK, "unit-test-key"));
        acc.setKeyStatus(0); acc.setStatus(0); acc.setSignAlgorithm("HMAC-SHA256");
        acc.setIpWhitelist(ip == null ? "" : ip);
        accountMapper.insert(acc);
        OrgProductDO op = new OrgProductDO();
        op.setOrgId(org.getId()); op.setProductId(100L); op.setProductCode("P000001"); op.setStatus(0);
        orgProductMapper.insert(op);
        return appKey;
    }

    private OrgAuthVerifyReqDTO signedReq(String appKey, String nonce, String ip) {
        OrgAuthVerifyReqDTO r = new OrgAuthVerifyReqDTO();
        r.setAppKey(appKey);
        r.setTimestamp(Long.toString(System.currentTimeMillis()));
        r.setNonce(nonce);
        r.setBodyDigest(CryptoSignatures.sha256Hex("{}"));
        r.setProductCode("P000001");
        r.setClientIp(ip);
        String payload = CryptoSignatures.buildSignPayload(appKey, r.getTimestamp(), nonce, r.getBodyDigest());
        r.setSignature(CryptoSignatures.hmacSha256Hex(SK, payload));
        return r;
    }

    @Test
    void allChecksPass() {
        seedHappyPath("AKok", "1.2.3.4");
        OrgAuthVerifyRespDTO resp = service.verify(signedReq("AKok", "n1", "1.2.3.4"));
        assertThat(resp.isPass()).isTrue();
        assertThat(resp.getOrgCode()).isEqualTo("ORG000001");
        assertThat(resp.getPlatformCode()).isEqualTo("0000");
    }

    @Test
    void unknownAppKey_1004() {
        assertThat(service.verify(signedReq("AKnope", "n1", "1.2.3.4")).getPlatformCode()).isEqualTo("1004");
    }

    @Test
    void badSignature_1001() {
        seedHappyPath("AKsig", "1.2.3.4");
        OrgAuthVerifyReqDTO r = signedReq("AKsig", "n1", "1.2.3.4");
        r.setSignature("deadbeef");
        assertThat(service.verify(r).getPlatformCode()).isEqualTo("1001");
    }

    @Test
    void staleTimestamp_1002() {
        seedHappyPath("AKts", "1.2.3.4");
        OrgAuthVerifyReqDTO r = signedReq("AKts", "n1", "1.2.3.4");
        r.setTimestamp(Long.toString(System.currentTimeMillis() - 600_000)); // 10 分钟前
        assertThat(service.verify(r).getPlatformCode()).isEqualTo("1002");
    }

    @Test
    void replayNonce_1002() {
        seedHappyPath("AKrp", "1.2.3.4");
        assertThat(service.verify(signedReq("AKrp", "dup", "1.2.3.4")).isPass()).isTrue();
        assertThat(service.verify(signedReq("AKrp", "dup", "1.2.3.4")).getPlatformCode()).isEqualTo("1002");
    }

    @Test
    void ipNotInWhitelist_1003() {
        seedHappyPath("AKip", "10.0.0.0/8");
        assertThat(service.verify(signedReq("AKip", "n1", "1.2.3.4")).getPlatformCode()).isEqualTo("1003");
        assertThat(service.verify(signedReq("AKip", "n2", "10.1.2.3")).isPass()).isTrue();
    }

    @Test
    void orgDisabled_1005() {
        seedHappyPath("AKorg", "1.2.3.4");
        OrgDO org = orgMapper.selectList().get(0);
        org.setStatus(1);
        orgMapper.updateById(org);
        assertThat(service.verify(signedReq("AKorg", "n1", "1.2.3.4")).getPlatformCode()).isEqualTo("1005");
    }

    @Test
    void productNotOpened_1006() {
        OrgDO org = new OrgDO();
        org.setOrgCode("ORG000002"); org.setName("机构"); org.setStatus(0);
        orgMapper.insert(org);
        OrgAccountDO acc = new OrgAccountDO();
        acc.setOrgId(org.getId()); acc.setAppKey("AKnoprod");
        acc.setSecretKeyCipher(AesCipher.encrypt(SK, "unit-test-key"));
        acc.setKeyStatus(0); acc.setStatus(0); acc.setIpWhitelist("");
        accountMapper.insert(acc);
        assertThat(service.verify(signedReq("AKnoprod", "n1", "9.9.9.9")).getPlatformCode()).isEqualTo("1006");
    }

    @Test
    void productExpired_1007() {
        seedHappyPath("AKexp", "1.2.3.4");
        OrgProductDO op = orgProductMapper.selectList().get(0);
        op.setExpireTime(LocalDateTime.now().minusDays(1));
        orgProductMapper.updateById(op);
        assertThat(service.verify(signedReq("AKexp", "n1", "1.2.3.4")).getPlatformCode()).isEqualTo("1007");
    }
}
