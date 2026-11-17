package com.chinasofti.huateng.account.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "employee-card")
public class EmployeeCardOutboundProperties {

    private String appRegisterUrl = "";


    private String accQueryUrl = "";


    private String accActivateUrl = "";


    private String providerId = "06";


    private String charset = "UTF-8";


    private String format = "json";


    private String deviceId = "ITP";


    private String signType = "00";


    private int connectTimeoutMs = 3000;


    private int readTimeoutMs = 10000;

    public String getAppRegisterUrl() {
        return appRegisterUrl;
    }

    public void setAppRegisterUrl(String appRegisterUrl) {
        this.appRegisterUrl = appRegisterUrl;
    }

    public String getAccQueryUrl() {
        return accQueryUrl;
    }

    public void setAccQueryUrl(String accQueryUrl) {
        this.accQueryUrl = accQueryUrl;
    }

    public String getAccActivateUrl() {
        return accActivateUrl;
    }

    public void setAccActivateUrl(String accActivateUrl) {
        this.accActivateUrl = accActivateUrl;
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

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
