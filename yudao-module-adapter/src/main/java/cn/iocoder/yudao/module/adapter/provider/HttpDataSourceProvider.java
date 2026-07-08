package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.iocoder.yudao.module.adapter.engine.EngineResult;
import cn.iocoder.yudao.module.adapter.engine.HttpAdapterEngine;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.ADAPTER_ENGINE_INVOKE_FAILED;

@Component
public class HttpDataSourceProvider implements DataSourceProvider {

    @Resource
    private HttpAdapterEngine engine;

    @Override
    public String type() { return "HTTP"; }

    @Override
    public ProviderResponse invoke(ProviderRequest request) {
        if (request.getDsInterfaceId() == null) {
            throw exception(ADAPTER_ENGINE_INVOKE_FAILED);
        }
        EngineResult r = engine.invoke(request.getDsInterfaceId(), request.getParams());
        ProviderResponse resp = new ProviderResponse();
        resp.setRawCode(r.getPlatformCode());
        resp.setPlatformCode(r.getPlatformCode());
        resp.setData(r.getMappedData());
        return resp;
    }
}
