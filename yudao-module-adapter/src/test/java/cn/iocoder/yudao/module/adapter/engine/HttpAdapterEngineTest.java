package cn.iocoder.yudao.module.adapter.engine;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceParamMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsResponseCodeMapper;
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

@Import({HttpAdapterEngine.class, ResponseCodeResolver.class, HttpInvoker.class,
        HttpAdapterEngineTest.MockHttpConfig.class})
class HttpAdapterEngineTest extends BaseDbUnitTest {

    @Resource private HttpAdapterEngine engine;
    @Resource private DsInterfaceMapper interfaceMapper;
    @Resource private DsInterfaceParamMapper paramMapper;
    @Resource private DsResponseCodeMapper codeMapper;
    @Resource private MockRestServiceServer mockServer;

    @TestConfiguration
    static class MockHttpConfig {
        // 注意：adapterRestClientBuilder() 声明 MockRestServiceServer 参数，强制 Spring 在构建
        // RestClient.Builder bean 之前先创建/绑定 mockRestServiceServer bean。
        // HttpAdapterEngine 对 HttpInvoker 的 @Resource 字段注入会在容器刷新早期就触发
        // HttpInvoker 构造（其构造器内一次性 build RestClient），若不显式声明此依赖，
        // Spring 按 bean 定义注册顺序创建单例时可能先构建出未绑定 mock 的 builder，
        // 导致 HttpInvoker 持有真实（非 mock）client，请求打到真实网络。
        private final RestClient.Builder builder = RestClient.builder();
        @Bean MockRestServiceServer mockRestServiceServer() { return MockRestServiceServer.bindTo(builder).build(); }
        @Bean RestClient.Builder adapterRestClientBuilder(MockRestServiceServer mockRestServiceServer) { return builder; }
    }

    private Long newInterface(String ifCode, Long dsId, String uri) {
        DsInterfaceDO dif = new DsInterfaceDO();
        dif.setIfCode(ifCode); dif.setName("接口"); dif.setDataSourceId(dsId);
        dif.setUri(uri); dif.setMethod("POST"); dif.setMsgFormat(1);
        dif.setStatus(0); dif.setVersion("v1"); dif.setTimeoutMs(3000); dif.setRetryCount(0);
        interfaceMapper.insert(dif);
        return dif.getId();
    }

    private void outParam(Long ifId, String platformField, String jsonPath) {
        DsInterfaceParamDO p = new DsInterfaceParamDO();
        p.setDsInterfaceId(ifId); p.setParamDirection(2);
        p.setPlatformField(platformField); p.setJsonPath(jsonPath);
        paramMapper.insert(p);
    }

    @Test
    void invoke_rendersCallsExtractsAndMapsCode() {
        Long ifId = newInterface("IF000001", 10L, "http://upstream/company");
        DsInterfaceParamDO in = new DsInterfaceParamDO();
        in.setDsInterfaceId(ifId); in.setParamDirection(1);
        in.setPlatformField("idNo"); in.setProviderField("cert_no");
        paramMapper.insert(in);
        outParam(ifId, "companyName", "$.data.entName");
        outParam(ifId, "__rawCode__", "$.code");
        DsResponseCodeDO rc = new DsResponseCodeDO();
        rc.setDataSourceId(10L); rc.setDsInterfaceId(ifId); rc.setRawCode("0");
        rc.setPlatformCode("0000"); rc.setSuccess(true); rc.setCharge(true);
        rc.setRetryable(false); rc.setTriggerSwitch(false);
        codeMapper.insert(rc);

        mockServer.expect(requestTo("http://upstream/company"))
                .andRespond(withSuccess("{\"code\":\"0\",\"data\":{\"entName\":\"某某公司\"}}",
                        MediaType.APPLICATION_JSON));

        EngineResult r = engine.invoke(ifId, Map.of("idNo", "110101199001011234"));

        assertThat(r.getPlatformCode()).isEqualTo("0000");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isCharge()).isTrue();
        assertThat(r.getMappedData()).containsEntry("companyName", "某某公司");
        assertThat(r.getMappedData()).doesNotContainKey("__rawCode__");
        assertThat(r.getRawCall().getStatusCode()).isEqualTo(200);
        mockServer.verify();
    }

    @Test
    void invoke_unmappedRawCode_yields3001() {
        Long ifId = newInterface("IF000002", 20L, "http://upstream/x");
        outParam(ifId, "__rawCode__", "$.code");
        mockServer.expect(requestTo("http://upstream/x"))
                .andRespond(withSuccess("{\"code\":\"E404\"}", MediaType.APPLICATION_JSON));

        EngineResult r = engine.invoke(ifId, Map.of());
        assertThat(r.getPlatformCode()).isEqualTo("3001");
        assertThat(r.isCodeMapped()).isFalse();
    }
}
