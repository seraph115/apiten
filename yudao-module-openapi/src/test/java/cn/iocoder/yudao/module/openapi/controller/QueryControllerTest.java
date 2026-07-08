package cn.iocoder.yudao.module.openapi.controller;

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
            .standaloneSetup(new QueryController(new QueryOrchestrator(emptyObjectProvider(), emptyObjectProvider()))).build();

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
        QueryOrchestrator orchestrator = new QueryOrchestrator(emptyObjectProvider(), emptyObjectProvider());
        assertThat(orchestrator.query("P000001", Map.of(), "1943026717538291712").getFlowNo())
                .isEqualTo("1943026717538291712");
    }

    @Test
    void orchestrator_query_withNullFlowNo_generatesFlowNo() {
        QueryOrchestrator orchestrator = new QueryOrchestrator(emptyObjectProvider(), emptyObjectProvider());
        assertThat(orchestrator.query("P000001", Map.of(), null).getFlowNo()).isNotBlank();
    }

    private static <T> org.springframework.beans.factory.ObjectProvider<T> emptyObjectProvider() {
        return new org.springframework.beans.factory.ObjectProvider<T>() {
            @Override public T getObject(Object... args) { throw new UnsupportedOperationException(); }
            @Override public T getIfAvailable() { return null; }
            @Override public T getIfUnique() { return null; }
            @Override public T getObject() { throw new UnsupportedOperationException(); }
        };
    }
}
