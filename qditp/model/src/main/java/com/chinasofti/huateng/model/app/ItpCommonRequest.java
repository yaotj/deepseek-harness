package com.chinasofti.huateng.model.app;

/**
 * ITP / APP / ACC 公共请求报文骨架，全项目唯一实现。
 *
 * <p>字段集来自甲方接口规范的公共参数部分：providerId、charset、format、timestamp、
 * deviceId、signType、sign、bizData。原先在 fep-app-server、fep-acc-server、
 * fep-dev-server、fep-alipay-server、account-server、collect-pay-server、
 * acc-secure-server 各有一份字段完全相同的副本，已于 2026-09-11 全部收口到本类。</p>
 *
 * <p><b>本类只承载报文骨架，不承载签名语义。</b>各链路的加签与验签算法互不相同
 * （account-server 的摘要式 SHA1/MD5 + signKey、acc-secure-server 的
 * AccSecureSignUtils、pay-sign-server 的 fastjson2 摘要、collect-pay-server 的
 * signType=00 不签、渠道方向的 RSA），**NEVER** 因为共用本类就把签名逻辑也统一，
 * 那会同时改动多个已与对端约定好的链路，属安全红线。</p>
 *
 * <p>入向 form-data 场景请使用 {@link ItpCommonFormRequest}，其 bizData 为 JSON 字符串。</p>
 *
 * @param <T> 业务参数类型，出向为具体 DTO、入向 form-data 为 String
 */
public class ItpCommonRequest<T> {
    private String providerId;
    private String charset;
    private String format;
    private String timestamp;
    private String deviceId;
    private String signType;
    private String sign;
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

    /**
     * 日志友好输出。sign 恒定脱敏，**NEVER** 改成打印真实签名值。
     */
    @Override
    public String toString() {
        return "ItpCommonRequest{" +
                "providerId='" + providerId + '\'' +
                ", charset='" + charset + '\'' +
                ", format='" + format + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", signType='" + signType + '\'' +
                ", sign='***'" +
                ", bizData=" + bizData +
                '}';
    }
}
