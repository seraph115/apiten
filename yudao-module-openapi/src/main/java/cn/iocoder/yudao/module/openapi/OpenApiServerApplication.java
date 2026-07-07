package cn.iocoder.yudao.module.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class OpenApiServerApplication {
    public static void main(String[] args) { SpringApplication.run(OpenApiServerApplication.class, args); }
}
