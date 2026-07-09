package cn.iocoder.yudao.module.openapi.controller;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.apiten.common.route.RouteResolveRespDTO;
import cn.iocoder.yudao.module.openapi.client.AdapterClient;
import cn.iocoder.yudao.module.openapi.client.RouteClient;
import cn.iocoder.yudao.module.openapi.service.QueryOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QueryControllerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new QueryController(
                    new QueryOrchestrator(emptyObjectProvider(), emptyObjectProvider(), emptyObjectProvider())))
            .build();

    @Test
    void query_returnsUnifiedResponseWithFlowNo() throws Exception {
        mvc.perform(post("/openapi/v1/P1001001/query")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"某某公司\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.productCode").value("P1001001"))
                .andExpect(jsonPath("$.flowNo").isString())
                .andExpect(jsonPath("$.data.mock").value(true));
    }

    @Test
    void query_withFlowNoHeader_passesThroughGatewayFlowNo() throws Exception {
        mvc.perform(post("/openapi/v1/P1001001/query")
                        .contentType(APPLICATION_JSON)
                        .header("X-Flow-No", "1943026717538291712")
                        .content("{\"name\":\"某某公司\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowNo").value("1943026717538291712"));
    }

    @Test
    void query_withoutFlowNoHeader_generatesFlowNo() throws Exception {
        mvc.perform(post("/openapi/v1/P1001001/query")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"某某公司\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowNo").isNotEmpty());
    }

    @Test
    void orchestrator_query_withFixedFlowNo_returnsSameFlowNo() {
        QueryOrchestrator orchestrator = new QueryOrchestrator(emptyObjectProvider(), emptyObjectProvider(),
                emptyObjectProvider());
        assertThat(orchestrator.query("P000001", Map.of(), "1943026717538291712").getFlowNo())
                .isEqualTo("1943026717538291712");
    }

    @Test
    void orchestrator_query_withNullFlowNo_generatesFlowNo() {
        QueryOrchestrator orchestrator = new QueryOrchestrator(emptyObjectProvider(), emptyObjectProvider(),
                emptyObjectProvider());
        assertThat(orchestrator.query("P000001", Map.of(), null).getFlowNo()).isNotBlank();
    }

    // 路由解析到 dsInterfaceId：stub AdapterClient 捕获入参，断言 ProviderRequest.dsInterfaceId 透传
    @Test
    void query_resolvesRoute_setsDsInterfaceIdOnProviderRequest() {
        java.util.concurrent.atomic.AtomicReference<Long> capturedDsInterfaceId = new java.util.concurrent.atomic.AtomicReference<>();
        AdapterClient stubAdapter = req -> {
            capturedDsInterfaceId.set(req.getDsInterfaceId());
            ProviderResponse resp = new ProviderResponse();
            resp.setPlatformCode("0000");
            resp.setData(Map.of("hit", true));
            return resp;
        };
        RouteClient stubRoute = (productCode, orgId) -> RouteResolveRespDTO.of(555L, "ROUTE_CONFIG");

        QueryOrchestrator orchestrator = new QueryOrchestrator(fixedObjectProvider(stubAdapter),
                emptyObjectProvider(), fixedObjectProvider(stubRoute));
        var resp = orchestrator.query("P000001", Map.of(), null, 1L);

        assertThat(capturedDsInterfaceId.get()).isEqualTo(555L);
        assertThat(resp.getCode()).isEqualTo("0000");
    }

    // 路由无目标：返回 3005，不调 adapter
    @Test
    void query_routeNoTarget_returns3005_withoutCallingAdapter() {
        java.util.concurrent.atomic.AtomicReference<Boolean> adapterInvoked = new java.util.concurrent.atomic.AtomicReference<>(false);
        AdapterClient stubAdapter = req -> {
            adapterInvoked.set(true);
            ProviderResponse resp = new ProviderResponse();
            resp.setPlatformCode("0000");
            return resp;
        };
        RouteClient stubRoute = (productCode, orgId) -> RouteResolveRespDTO.noTarget();

        QueryOrchestrator orchestrator = new QueryOrchestrator(fixedObjectProvider(stubAdapter),
                emptyObjectProvider(), fixedObjectProvider(stubRoute));
        var resp = orchestrator.query("P000001", Map.of(), null, 1L);

        assertThat(resp.getCode()).isEqualTo("3005");
        assertThat(adapterInvoked.get()).isFalse();
    }

    private static <T> org.springframework.beans.factory.ObjectProvider<T> emptyObjectProvider() {
        return new org.springframework.beans.factory.ObjectProvider<T>() {
            @Override public T getObject(Object... args) { throw new UnsupportedOperationException(); }
            @Override public T getIfAvailable() { return null; }
            @Override public T getIfUnique() { return null; }
            @Override public T getObject() { throw new UnsupportedOperationException(); }
        };
    }

    private static <T> org.springframework.beans.factory.ObjectProvider<T> fixedObjectProvider(T instance) {
        return new org.springframework.beans.factory.ObjectProvider<T>() {
            @Override public T getObject(Object... args) { return instance; }
            @Override public T getIfAvailable() { return instance; }
            @Override public T getIfUnique() { return instance; }
            @Override public T getObject() { return instance; }
        };
    }
}
