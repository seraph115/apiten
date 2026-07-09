package cn.iocoder.yudao.module.route.controller.rpc;

import cn.apiten.common.route.RouteResolveRespDTO;
import jakarta.annotation.security.PermitAll;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.route.service.resolve.RouteResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;

@RestController
@RequestMapping("/rpc-api/route")
public class RouteResolveRpcController {

    @Resource
    private RouteResolver routeResolver;

    @GetMapping("/resolve")
    @PermitAll
    @TenantIgnore
    public RouteResolveRespDTO resolve(@RequestParam("productCode") String productCode,
            @RequestParam(value = "orgId", required = false) Long orgId) {
        return routeResolver.resolve(productCode, orgId);
    }
}
