package cn.iocoder.yudao.module.adapter.provider;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderRegistry {

    private final Map<String, DataSourceProvider> byType = new HashMap<>();

    public ProviderRegistry(List<DataSourceProvider> providers) {
        for (DataSourceProvider p : providers) {
            byType.put(p.type(), p);
        }
    }

    public DataSourceProvider get(String type) {
        DataSourceProvider p = byType.get(type);
        return p != null ? p : byType.get("MOCK");
    }
}
