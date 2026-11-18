package com.chinasofti.huateng.acc.security.server;

import com.chinasofti.huateng.acc.security.server.config.SecurityConfig;
import com.chinasofti.huateng.acc.security.server.reconnect.handle.ReConnectManager;
import com.chinasofti.huateng.acc.security.server.socket.SocketClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SecurityServerApplication implements CommandLineRunner {

    @Autowired
    private SocketClient client;

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private ReConnectManager reConnectManager;

    public static void main(String[] args) {
        SpringApplication.run(SecurityServerApplication.class, args);
    }

    @Override
    public void run(String... args) {
        // 初始建连：按 channelPoolSize 目标值发起（connect 非阻塞，成功与否由池补齐任务兜底）
        for (int i = 0; i < securityConfig.getChannelPoolSize(); i++) {
            client.start();
        }
        // 启动即挂池补齐任务：初始建连失败或后续断连都由它补齐，补满自停
        reConnectManager.reConnect(null);
    }

}
