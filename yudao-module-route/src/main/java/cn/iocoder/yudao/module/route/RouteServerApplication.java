package cn.iocoder.yudao.module.route;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "cn.iocoder.yudao.module.route")
public class RouteServerApplication {
    public static void main(String[] args) { SpringApplication.run(RouteServerApplication.class, args); }
}
