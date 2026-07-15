package com.chinasofti.huateng.accsecure.model.common;

/**
 * ACC 通用响应报文。
 */
public class AccCommonResponse<T> {
    /**
     * 商户编码。
     */
    private String providerId;

    /**
     * 字符集。
     */
    private String charset;

    /**
     * 数据格式。
     */
    private String format;

    /**
     * 响应时间。
     */
    private String timestamp;

    /**
     * 设备编码。
     */
    private String deviceId;

    /**
     * 签名类型。
     */
    private String signType;

    /**
     * 签名值。
     */
    private String sign;

    /**
     * 业务数据。
     */
    private T bizData;

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

    public T getBizData() {
        return bizData;
    }

    public void setBizData(T bizData) {
        this.bizData = bizData;
    }
}
