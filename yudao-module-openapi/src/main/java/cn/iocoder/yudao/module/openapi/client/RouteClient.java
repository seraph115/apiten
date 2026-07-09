package cn.iocoder.yudao.module.openapi.client;

import cn.apiten.common.route.RouteResolveRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "route-server", path = "/rpc-api/route")
public interface RouteClient {
    @GetMapping("/resolve")
    RouteResolveRespDTO resolve(@RequestParam("productCode") String productCode,
            @RequestParam(value = "orgId", required = false) Long orgId);
}
