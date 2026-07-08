package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceParamMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsResponseCodeMapper;
import cn.iocoder.yudao.module.adapter.engine.HttpAdapterEngine;
import cn.iocoder.yudao.module.adapter.engine.HttpInvoker;
import cn.iocoder.yudao.module.adapter.engine.ResponseCodeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import jakarta.annotation.Resource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Import({HttpDataSourceProvider.class, HttpAdapterEngine.class, ResponseCodeResolver.class,
        HttpInvoker.class, HttpDataSourceProviderTest.MockHttpConfig.class})
class HttpDataSourceProviderTest extends BaseDbUnitTest {

    @Resource private HttpDataSourceProvider provider;
    @Resource private DsInterfaceMapper interfaceMapper;
    @Resource private DsInterfaceParamMapper paramMapper;
    @Resource private DsResponseCodeMapper codeMapper;
    @Resource private MockRestServiceServer mockServer;

    @TestConfiguration
    static class MockHttpConfig {
        // 同 Task 6 HttpAdapterEngineTest 的 bean 顺序修复：adapterRestClientBuilder() 显式声明
        // MockRestServiceServer 参数，强制 Spring 先绑定 mock 再构建 builder，否则 HttpInvoker
        // 会持有未绑定 mock 的真实 client，导致请求打到真实网络。
        private final RestClient.Builder builder = RestClient.builder();
        @Bean MockRestServiceServer mockRestServiceServer() { return MockRestServiceServer.bindTo(builder).build(); }
        @Bean RestClient.Builder adapterRestClientBuilder(MockRestServiceServer mockRestServiceServer) { return builder; }
    }

    @Test
    void invoke_viaEngine_returnsMappedProviderResponse() {
        DsInterfaceDO dif = new DsInterfaceDO();
        dif.setIfCode("IF000001"); dif.setName("x"); dif.setDataSourceId(10L);
        dif.setUri("http://up/q"); dif.setMethod("POST"); dif.setMsgFormat(1);
        dif.setStatus(0); dif.setVersion("v1"); dif.setTimeoutMs(3000); dif.setRetryCount(0);
        interfaceMapper.insert(dif);
        Long ifId = dif.getId();
        DsInterfaceParamDO rawCodeMap = new DsInterfaceParamDO();
        rawCodeMap.setDsInterfaceId(ifId); rawCodeMap.setParamDirection(2);
        rawCodeMap.setPlatformField("__rawCode__"); rawCodeMap.setJsonPath("$.code");
        paramMapper.insert(rawCodeMap);
        DsResponseCodeDO rc = new DsResponseCodeDO();
        rc.setDataSourceId(10L); rc.setDsInterfaceId(ifId); rc.setRawCode("0");
        rc.setPlatformCode("0000"); rc.setSuccess(true); rc.setCharge(true);
        rc.setRetryable(false); rc.setTriggerSwitch(false);
        codeMapper.insert(rc);

        mockServer.expect(requestTo("http://up/q"))
                .andRespond(withSuccess("{\"code\":\"0\"}", MediaType.APPLICATION_JSON));

        ProviderRequest req = new ProviderRequest();
        req.setDsInterfaceId(ifId);
        req.setParams(Map.of());
        ProviderResponse resp = provider.invoke(req);

        assertThat(provider.type()).isEqualTo("HTTP");
        assertThat(resp.getPlatformCode()).isEqualTo("0000");
    }
}
