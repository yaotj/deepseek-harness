package org.qd.scg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UserRouteGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserRouteGatewayApplication.class, args);
    }
}
