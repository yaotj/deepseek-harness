package com.chinasofti.huateng.acc.security.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 加密服务配置
 *
 * @author 49935
 */
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityConfig {
    private String firstIp;

    private int firstPort;

    private int secondPort;

    private int connectOutTime;

    private int readOutTime;

    private String index;

    private String publicKey;

    private String privateKey;

    public String getFirstIp() {
        return firstIp;
    }

    public void setFirstIp(String firstIp) {
        this.firstIp = firstIp;
    }

    public int getFirstPort() {
        return firstPort;
    }

    public void setFirstPort(int firstPort) {
        this.firstPort = firstPort;
    }

    public int getSecondPort() {
        return secondPort;
    }

    public void setSecondPort(int secondPort) {
        this.secondPort = secondPort;
    }

    public int getConnectOutTime() {
        return connectOutTime;
    }

    public void setConnectOutTime(int connectOutTime) {
        this.connectOutTime = connectOutTime;
    }

    public int getReadOutTime() {
        return readOutTime;
    }

    public void setReadOutTime(int readOutTime) {
        this.readOutTime = readOutTime;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }
}
