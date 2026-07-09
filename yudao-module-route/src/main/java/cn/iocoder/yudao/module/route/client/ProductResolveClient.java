package cn.iocoder.yudao.module.route.client;

import cn.apiten.common.route.ProductDefaultRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-server", path = "/rpc-api/product")
public interface ProductResolveClient {
    @GetMapping("/resolve-default")
    ProductDefaultRespDTO resolveDefault(@RequestParam("productCode") String productCode);
}
