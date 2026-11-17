package com.chinasofti.huateng.blacklist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "blacklist.hce-file")
public class HceBlacklistFileProperties {

    private String ftpHost;

    private int ftpPort = 21;

    private String ftpUsername;

    private String ftpPassword;

    private String ftpRemoteDir = "/blacklist/";

    private String localDir = "/tmp/hce-blacklist";

    private int connectTimeoutMillis = 10000;

    private int dataTimeoutMillis = 60000;

    private String filePrefix = "PRM.0013.9700.";

    private String paramType = "0013";

    public String getFtpHost() {
        return ftpHost;
    }

    public void setFtpHost(String ftpHost) {
        this.ftpHost = ftpHost;
    }

    public int getFtpPort() {
        return ftpPort;
    }

    public void setFtpPort(int ftpPort) {
        this.ftpPort = ftpPort;
    }

    public String getFtpUsername() {
        return ftpUsername;
    }

    public void setFtpUsername(String ftpUsername) {
        this.ftpUsername = ftpUsername;
    }

    public String getFtpPassword() {
        return ftpPassword;
    }

    public void setFtpPassword(String ftpPassword) {
        this.ftpPassword = ftpPassword;
    }

    public String getFtpRemoteDir() {
        return ftpRemoteDir;
    }

    public void setFtpRemoteDir(String ftpRemoteDir) {
        this.ftpRemoteDir = ftpRemoteDir;
    }

    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(String localDir) {
        this.localDir = localDir;
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

    public String getFilePrefix() {
        return filePrefix;
    }

    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }
}
