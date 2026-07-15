package com.chinasofti.huateng.collectpay.model.request;

/**
 * 请求公共参数基类（7.5.1.1 请求公共参数）。
 */
public class BaseRequestDTO {
    /**
     * 商户编码。
     * 01:青岛地铁APP, 02:TVM, 03:BOM, 04:AGM, 05:ACC, 06:ITP, 07:STT, 其他预留。
     */
    private String providerId;

    /**
     * 入参字符集：UTF-8。
     */
    private String charset;

    /**
     * 数据格式：json。
     */
    private String format;

    /**
     * 请求时间，格式：YYYYMMDDHHMMSS。
     */
    private String timestamp;

    /**
     * 设备编码。
     */
    private String deviceId;

    /**
     * 签名类型。
     * 00：不签名, 01：sha1withrsa, 02：MD5。
     */
    private String signType;

    /**
     * 签名值。
     */
    private String sign;

    /**
     * 业务数据（JSON字符串）。
     */
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
        return "BaseRequestDTO{" +
                "providerId='" + providerId + '\'' +
                ", charset='" + charset + '\'' +
                ", format='" + format + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", signType='" + signType + '\'' +
                ", sign='" + sign + '\'' +
                ", bizData='" + bizData + '\'' +
                '}';
    }
}
