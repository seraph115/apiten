package cn.iocoder.yudao.module.adapter.controller;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.iocoder.yudao.module.adapter.provider.DataSourceProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/adapter/v1")
public class InvokeController {

    private final DataSourceProvider provider; // P0 仅 MockProvider；P1 起按数据源类型路由

    public InvokeController(DataSourceProvider provider) { this.provider = provider; }

    @PostMapping("/invoke")
    public ProviderResponse invoke(@RequestBody ProviderRequest request) {
        return provider.invoke(request);
    }
}
