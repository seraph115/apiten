package cn.iocoder.yudao.module.adapter.engine;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpInvokerTest {

    @Test
    void postReturnsBodyAndStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://upstream/query"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string("{\"cert\":\"x\"}"))
              .andRespond(withSuccess("{\"code\":\"0\",\"data\":{}}", MediaType.APPLICATION_JSON));

        HttpInvoker invoker = new HttpInvoker(builder);
        HttpCallResult r = invoker.call("POST", "http://upstream/query",
                Map.of("Content-Type", "application/json"), "{\"cert\":\"x\"}", 3000, 0);

        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(r.getRawResponseBody()).contains("\"code\":\"0\"");
        assertThat(r.getRequestUrl()).isEqualTo("http://upstream/query");
        server.verify();
    }

    @Test
    void serverError_returnedNotThrown() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://upstream/e"))
              .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        HttpInvoker invoker = new HttpInvoker(builder);
        HttpCallResult r = invoker.call("GET", "http://upstream/e", Map.of(), null, 3000, 0);
        assertThat(r.getStatusCode()).isEqualTo(500);
        assertThat(r.getRawResponseBody()).isEqualTo("boom");
    }
}
