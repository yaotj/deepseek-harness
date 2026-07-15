package com.chinasofti.huateng.acc.security.server;

import com.chinasofti.huateng.acc.security.server.socket.SocketClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SecurityServerApplication implements CommandLineRunner {

    @Autowired
    private SocketClient client;

    @Value("${thread.corePoolSize}")
    private int corePoolSize;

    public static void main(String[] args) {
        SpringApplication.run(SecurityServerApplication.class, args);
    }

    @Override
    public void run(String... args) {
        for (int i = 0; i < corePoolSize; i++) {
            client.start();
        }
    }

}
