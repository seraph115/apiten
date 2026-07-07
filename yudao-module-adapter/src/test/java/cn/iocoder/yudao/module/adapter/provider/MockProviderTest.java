package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MockProviderTest {

    @Test
    void invoke_echoesParamsWithSuccessCode() {
        MockProvider p = new MockProvider();
        ProviderRequest req = new ProviderRequest();
        req.setProductCode("P1001001");
        req.setParams(Map.of("name", "某某公司"));
        ProviderResponse resp = p.invoke(req);
        assertThat(p.type()).isEqualTo("MOCK");
        assertThat(resp.getPlatformCode()).isEqualTo("0000");
        assertThat(resp.getRawCode()).isEqualTo("MOCK_OK");
        assertThat(resp.getData()).containsEntry("name", "某某公司");
    }
}
