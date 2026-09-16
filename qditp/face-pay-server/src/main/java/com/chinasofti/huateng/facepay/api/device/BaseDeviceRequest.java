package com.chinasofti.huateng.facepay.api.device;

/**
 * 设备侧公共请求参数。字段名与旧 {@code BaseRequestDTO} <b>逐字一致</b>，因为设备发的就是这些名字。
 *
 * <p>接入形态（不可改）：{@code application/x-www-form-urlencoded} + {@code @ModelAttribute} 表单
 * 绑定，业务参数在 {@code bizData} 里是一个 JSON 字符串，需二次反序列化。</p>
 *
 * <p><b>本链路没有验签。</b>{@code sign} / {@code signType} 只是被拷进 DTO，旧实现全模块无验签代码
 * （AGENTS.md §2.2.1）。新增鉴权属契约变更，NEVER 在重写里顺手加。</p>
 */
public class BaseDeviceRequest {

    /** 商户编码：01-APP，02-TVM，03-BOM，04-AGM，05-ACC，06-ITP，07-STT。 */
    private String providerId;

    /** 入参字符集：UTF-8。 */
    private String charset;

    /** 数据格式：json。 */
    private String format;

    /** 请求时间，格式 YYYYMMDDHHMMSS。 */
    private String timestamp;

    /** 设备编码。**可能只出现在 bizData 里**，表单值为空时不得覆盖。 */
    private String deviceId;

    /** 签名类型：00-不签名，01-sha1withrsa，02-MD5。 */
    private String signType;

    /** 签名值。 */
    private String sign;

    /** 业务数据（JSON 字符串）。 */
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
