package cn.iocoder.yudao.module.openapi.client;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "adapter-server", path = "/adapter/v1")
public interface AdapterClient {
    @PostMapping("/invoke")
    ProviderResponse invoke(@RequestBody ProviderRequest request);
}
