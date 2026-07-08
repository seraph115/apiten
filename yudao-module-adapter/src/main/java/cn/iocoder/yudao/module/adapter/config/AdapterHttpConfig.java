package cn.iocoder.yudao.module.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Adapter 模块 HTTP 客户端配置——为 {@link cn.iocoder.yudao.module.adapter.engine.HttpInvoker}
 * 提供 {@link RestClient.Builder} 注入点
 */
@Configuration
public class AdapterHttpConfig {

    @Bean
    public RestClient.Builder adapterRestClientBuilder() {
        return RestClient.builder();
    }
}
