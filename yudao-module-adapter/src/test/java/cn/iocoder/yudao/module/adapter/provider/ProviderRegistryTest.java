package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderRegistryTest {

    static class StubProvider implements DataSourceProvider {
        private final String t;
        StubProvider(String t) { this.t = t; }
        public String type() { return t; }
        public ProviderResponse invoke(ProviderRequest r) {
            ProviderResponse resp = new ProviderResponse();
            resp.setPlatformCode(t);
            return resp;
        }
    }

    @Test
    void getByType() {
        ProviderRegistry reg = new ProviderRegistry(List.of(new StubProvider("MOCK"), new StubProvider("HTTP")));
        assertThat(reg.get("HTTP").type()).isEqualTo("HTTP");
        assertThat(reg.get("MOCK").type()).isEqualTo("MOCK");
    }

    @Test
    void unknownType_fallsBackToMock() {
        ProviderRegistry reg = new ProviderRegistry(List.of(new StubProvider("MOCK")));
        assertThat(reg.get("NOPE").type()).isEqualTo("MOCK");
    }
}
