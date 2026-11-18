package com.chinasofti.huateng.acc.security.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


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

    /** 加密机连接池目标大小（与 socket 线程池解耦，独立配置）。 */
    private int channelPoolSize = 10;

    /** 从连接池独占获取连接的最大排队等待毫秒数，超时抛 HsmUnavailableException。 */
    private long acquireTimeoutMs = 800;

    /** 是否开启空闲连接心跳保活（0x00 系统信息指令）。 */
    private boolean heartbeatEnabled = true;

    /** 连接空闲超过该秒数后对其发心跳。 */
    private long heartbeatIdleSeconds = 60;

    /** 心跳巡检周期（毫秒）。 */
    private long heartbeatPeriodMs = 30000;

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

    public int getChannelPoolSize() {
        return channelPoolSize;
    }

    public void setChannelPoolSize(int channelPoolSize) {
        this.channelPoolSize = channelPoolSize;
    }

    public long getAcquireTimeoutMs() {
        return acquireTimeoutMs;
    }

    public void setAcquireTimeoutMs(long acquireTimeoutMs) {
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    public boolean isHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    public void setHeartbeatEnabled(boolean heartbeatEnabled) {
        this.heartbeatEnabled = heartbeatEnabled;
    }

    public long getHeartbeatIdleSeconds() {
        return heartbeatIdleSeconds;
    }

    public void setHeartbeatIdleSeconds(long heartbeatIdleSeconds) {
        this.heartbeatIdleSeconds = heartbeatIdleSeconds;
    }

    public long getHeartbeatPeriodMs() {
        return heartbeatPeriodMs;
    }

    public void setHeartbeatPeriodMs(long heartbeatPeriodMs) {
        this.heartbeatPeriodMs = heartbeatPeriodMs;
    }
}
