package cn.iocoder.yudao.module.org.service.auth;

import cn.apiten.common.org.OrgAuthVerifyReqDTO;
import cn.apiten.common.org.OrgAuthVerifyRespDTO;

/**
 * 机构五重鉴权服务：账号 -> 时间戳 -> 签名 -> nonce 去重 -> IP 白名单 -> 账号状态 -> 机构状态 -> 产品有效期。
 */
public interface OrgAuthService {

    OrgAuthVerifyRespDTO verify(OrgAuthVerifyReqDTO req);
}
