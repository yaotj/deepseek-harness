package com.chinasofti.huateng.para.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "para.ftp")
public class ParaFtpProperties {

    private String ip;
    private int port = 21;
    private String username;
    private String password;
    /** ACC 放置参数文件的远端目录。 */
    private String remoteDir = "/parameter/cur/";
    private int connectTimeoutMillis = 10000;
    private int dataTimeoutMillis = 60000;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRemoteDir() {
        return remoteDir;
    }

    public void setRemoteDir(String remoteDir) {
        this.remoteDir = remoteDir;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getDataTimeoutMillis() {
        return dataTimeoutMillis;
    }

    public void setDataTimeoutMillis(int dataTimeoutMillis) {
        this.dataTimeoutMillis = dataTimeoutMillis;
    }
}
