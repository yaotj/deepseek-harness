package com.chinasofti.huateng.cardpool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "acc.secure")
public class AccSecureProperties {

    private String baseUrl;

    private String requestQrLogicNumListPath = "/ci/itp/requestQrLoigcNumList";

    private String providerId = "06";

    private String charset = "UTF-8";

    private String format = "json";

    private String deviceId = "ITP-CARD-POOL";

    private String signType = "00";

    private String signKey = "";

    private int connectTimeoutMillis = 10000;

    private int readTimeoutMillis = 30000;


    public String getBaseUrl() {
        return baseUrl;
    }


    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }


    public String getRequestQrLogicNumListPath() {
        return requestQrLogicNumListPath;
    }


    public void setRequestQrLogicNumListPath(String requestQrLogicNumListPath) {
        this.requestQrLogicNumListPath = requestQrLogicNumListPath;
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


    public String getSignKey() {
        return signKey;
    }


    public void setSignKey(String signKey) {
        this.signKey = signKey;
    }


    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }


    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }


    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }


    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }
}
