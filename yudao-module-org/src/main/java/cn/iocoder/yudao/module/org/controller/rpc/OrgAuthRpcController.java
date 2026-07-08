package cn.iocoder.yudao.module.org.controller.rpc;

import cn.apiten.common.org.OrgAuthVerifyReqDTO;
import cn.apiten.common.org.OrgAuthVerifyRespDTO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.org.service.auth.OrgAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;

/**
 * 内部 RPC 端点：五重鉴权校验。免登录（@PermitAll），忽略租户过滤（@TenantIgnore）。
 */
@RestController
@RequestMapping("/rpc-api/org-auth")
public class OrgAuthRpcController {

    @Resource
    private OrgAuthService orgAuthService;

    @PostMapping("/verify")
    @PermitAll
    @TenantIgnore
    public OrgAuthVerifyRespDTO verify(@RequestBody OrgAuthVerifyReqDTO req) {
        return orgAuthService.verify(req);
    }
}
