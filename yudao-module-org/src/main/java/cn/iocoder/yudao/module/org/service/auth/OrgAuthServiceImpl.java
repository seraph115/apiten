package cn.iocoder.yudao.module.org.service.auth;

import cn.apiten.common.api.PlatformErrorCode;
import cn.apiten.common.crypto.AesCipher;
import cn.apiten.common.crypto.CryptoSignatures;
import cn.apiten.common.org.OrgAuthVerifyReqDTO;
import cn.apiten.common.org.OrgAuthVerifyRespDTO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgAccountMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgProductMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;

@Component
public class OrgAuthServiceImpl implements OrgAuthService {

    @Value("${apiten.org.sk-secret:apiten-default-dev-key}")
    private String skSecret;
    @Value("${apiten.org.timestamp-window-ms:300000}")
    private long timestampWindowMs;

    @Resource private OrgAccountMapper accountMapper;
    @Resource private OrgMapper orgMapper;
    @Resource private OrgProductMapper orgProductMapper;
    @Resource private NonceStore nonceStore;

    @Override
    public OrgAuthVerifyRespDTO verify(OrgAuthVerifyReqDTO req) {
        // 1. 账号
        OrgAccountDO account = accountMapper.selectByAppKey(req.getAppKey());
        if (account == null) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.ACCOUNT_DISABLED);
        }
        // 2. 时间戳窗口
        long ts;
        try {
            ts = Long.parseLong(req.getTimestamp());
        } catch (Exception e) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.TIMESTAMP_INVALID);
        }
        if (Math.abs(System.currentTimeMillis() - ts) > timestampWindowMs) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.TIMESTAMP_INVALID);
        }
        // 3. 签名
        String sk = AesCipher.decrypt(account.getSecretKeyCipher(), skSecret);
        String expect = CryptoSignatures.hmacSha256Hex(sk,
                CryptoSignatures.buildSignPayload(req.getAppKey(), req.getTimestamp(), req.getNonce(), req.getBodyDigest()));
        if (!expect.equalsIgnoreCase(req.getSignature())) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.SIGN_ERROR);
        }
        // 4. nonce 去重（重放）——放在签名之后，避免未过签名的请求污染 nonce 存储
        if (!nonceStore.tryAcquire(req.getAppKey() + ":" + req.getNonce(), timestampWindowMs)) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.TIMESTAMP_INVALID);
        }
        // 5. IP 白名单
        if (!ipAllowed(account.getIpWhitelist(), req.getClientIp())) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.IP_FORBIDDEN);
        }
        // 6. 账号状态
        LocalDateTime now = LocalDateTime.now();
        boolean accountBad = !isZero(account.getStatus()) || !isZero(account.getKeyStatus())
                || (account.getExpireTime() != null && account.getExpireTime().isBefore(now));
        if (accountBad) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.ACCOUNT_DISABLED);
        }
        // 7. 机构
        OrgDO org = orgMapper.selectById(account.getOrgId());
        if (org == null || !isZero(org.getStatus())) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.ORG_DISABLED);
        }
        // 8. 产品有效期
        OrgProductDO op = orgProductMapper.selectByOrgAndProductCode(account.getOrgId(), req.getProductCode());
        if (op == null || !isZero(op.getStatus())) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.PRODUCT_UNAUTHORIZED);
        }
        if (op.getExpireTime() != null && op.getExpireTime().isBefore(now)) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.PRODUCT_EXPIRED);
        }
        if (op.getEffectiveTime() != null && op.getEffectiveTime().isAfter(now)) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.PRODUCT_UNAUTHORIZED);
        }
        return OrgAuthVerifyRespDTO.pass(org.getId(), account.getId(), org.getOrgCode());
    }

    private boolean isZero(Integer v) { return v != null && v == 0; }

    /** 空白名单放行；否则逐条精确/CIDR 匹配 */
    private boolean ipAllowed(String whitelist, String clientIp) {
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        for (String entry : whitelist.split(",")) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if (e.contains("/") ? cidrMatch(e, clientIp) : e.equals(clientIp)) {
                return true;
            }
        }
        return false;
    }

    private boolean cidrMatch(String cidr, String ip) {
        try {
            String[] parts = cidr.split("/");
            long net = ipv4ToLong(parts[0]);
            int prefix = Integer.parseInt(parts[1]);
            long mask = prefix == 0 ? 0 : (-1L << (32 - prefix)) & 0xFFFFFFFFL;
            return (ipv4ToLong(ip) & mask) == (net & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private long ipv4ToLong(String ip) {
        String[] o = ip.split("\\.");
        return (Long.parseLong(o[0]) << 24) | (Long.parseLong(o[1]) << 16)
                | (Long.parseLong(o[2]) << 8) | Long.parseLong(o[3]);
    }
}
