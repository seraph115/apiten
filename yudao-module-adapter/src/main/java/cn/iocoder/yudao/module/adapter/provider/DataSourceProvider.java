package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;

public interface DataSourceProvider {
    String type();
    ProviderResponse invoke(ProviderRequest request);
}
