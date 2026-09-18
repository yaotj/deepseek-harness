package com.chinasofti.huateng.accsimulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acc-simulator")
public class AccSimulatorProperties {

    private String fepAccBaseUrl = "http://localhost:9110";
    private String providerId = "06";
    private String charset = "UTF-8";
    private String format = "json";
    private String deviceId = "ACC-SIMULATOR";
    private String signType = "00";
    private int historyLimit = 50;

    public String getFepAccBaseUrl() {
        return fepAccBaseUrl;
    }

    public void setFepAccBaseUrl(String fepAccBaseUrl) {
        this.fepAccBaseUrl = fepAccBaseUrl;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }

    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }
}
