package cn.iocoder.yudao.module.adapter.controller;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.iocoder.yudao.module.adapter.provider.ProviderRegistry;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/adapter/v1")
public class InvokeController {

    private final ProviderRegistry registry;

    public InvokeController(ProviderRegistry registry) { this.registry = registry; }

    @PostMapping("/invoke")
    public ProviderResponse invoke(@RequestBody ProviderRequest request) {
        String type = request.getDsInterfaceId() != null ? "HTTP" : "MOCK";
        return registry.get(type).invoke(request);
    }
}
