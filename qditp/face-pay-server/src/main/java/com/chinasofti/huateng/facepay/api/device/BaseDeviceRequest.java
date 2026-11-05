package com.chinasofti.huateng.facepay.api.device;

public class BaseDeviceRequest {

    private String providerId;

    private String charset;

    private String format;

    private String timestamp;

    private String deviceId;

    private String signType;

    private String sign;

    private String bizData;

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

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
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

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getBizData() {
        return bizData;
    }

    public void setBizData(String bizData) {
        this.bizData = bizData;
    }

    @Override
    public String toString() {
        return "BaseDeviceRequest{providerId=" + providerId
                + ", charset=" + charset
                + ", format=" + format
                + ", timestamp=" + timestamp
                + ", deviceId=" + deviceId
                + ", signType=" + signType
                + ", bizData=" + bizData
                + '}';
    }
}
