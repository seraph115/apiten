package cn.iocoder.yudao.module.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Adapter 模块 HTTP 客户端配置——为 {@link cn.iocoder.yudao.module.adapter.engine.HttpInvoker}
 * 提供 {@link RestClient.Builder} 注入点
 */
@Configuration
public class AdapterHttpConfig {

    /**
     * 适配引擎 HTTP 客户端 builder，预置默认连接/读取超时，避免上游无响应时请求线程无界阻塞。
     * 按接口的动态 timeoutMs 留待后续（当前由此默认值兜底）。
     */
    @Bean
    public RestClient.Builder adapterRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return RestClient.builder().requestFactory(factory);
    }
}
