package cn.iocoder.yudao.module.openapi.controller;

import cn.iocoder.yudao.module.openapi.service.QueryOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QueryControllerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new QueryController(new QueryOrchestrator())).build();

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
}
