package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class MockProvider implements DataSourceProvider {

    @Override
    public String type() { return "MOCK"; }

    @Override
    public ProviderResponse invoke(ProviderRequest request) {
        ProviderResponse resp = new ProviderResponse();
        resp.setRawCode("MOCK_OK");
        resp.setPlatformCode("0000");
        resp.setData(new HashMap<>(request.getParams() == null ? Map.of() : request.getParams()));
        return resp;
    }
}
