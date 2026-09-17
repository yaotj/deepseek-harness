package com.chinasofti.huateng.cardpool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** ACC 安全接口（IF7B）调用配置，前缀 {@code acc.secure}。 */
@Component
@ConfigurationProperties(prefix = "acc.secure")
public class AccSecureProperties {

    /** ACC 服务根地址，未配置时申请动作直接失败。 */
    private String baseUrl;

    /** IF7B-01 请求逻辑卡号的接口路径。 */
    private String requestQrLogicNumListPath = "/ci/itp/requestQrLoigcNumList";

    /** 商户编码。 */
    private String providerId = "06";

    /** 入参字符集，规格要求 UTF-8。 */
    private String charset = "UTF-8";

    /** 数据格式，规格要求 json。 */
    private String format = "json";

    /** 设备编码。 */
    private String deviceId = "ITP-CARD-POOL";

    /** 签名类型，00-不签名，02-MD5。 */
    private String signType = "00";

    /** MD5 签名 key，仅 signType=02 时使用。 */
    private String signKey = "";

    /** 连接超时毫秒数。 */
    private int connectTimeoutMillis = 10000;

    /** 读写超时毫秒数。 */
    private int readTimeoutMillis = 30000;

    /**
     * 读取 ACC 服务根地址。
     *
     * @return ACC 服务根地址，未配置时申请动作直接失败
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置 ACC 服务根地址。
     *
     * @param baseUrl ACC 服务根地址
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 读取 IF7B-01 请求逻辑卡号的接口路径。
     *
     * @return IF7B-01 接口路径
     */
    public String getRequestQrLogicNumListPath() {
        return requestQrLogicNumListPath;
    }

    /**
     * 设置 IF7B-01 请求逻辑卡号的接口路径。
     *
     * @param requestQrLogicNumListPath IF7B-01 接口路径
     */
    public void setRequestQrLogicNumListPath(String requestQrLogicNumListPath) {
        this.requestQrLogicNumListPath = requestQrLogicNumListPath;
    }

    /**
     * 读取 ACC 商户编码。
     *
     * @return 商户编码
     */
    public String getProviderId() {
        return providerId;
    }

    /**
     * 设置 ACC 商户编码。
     *
     * @param providerId 商户编码
     */
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    /**
     * 读取入参字符集。
     *
     * @return 字符集，规格要求 UTF-8
     */
    public String getCharset() {
        return charset;
    }

    /**
     * 设置入参字符集。
     *
     * @param charset 字符集
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

    /**
     * 读取报文数据格式。
     *
     * @return 数据格式，规格要求 json
     */
    public String getFormat() {
        return format;
    }

    /**
     * 设置报文数据格式。
     *
     * @param format 数据格式
     */
    public void setFormat(String format) {
        this.format = format;
    }

    /**
     * 读取设备编码。
     *
     * @return 设备编码
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 设置设备编码。
     *
     * @param deviceId 设备编码
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * 读取签名类型。
     *
     * @return 签名类型，00-不签名，02-MD5
     */
    public String getSignType() {
        return signType;
    }

    /**
     * 设置签名类型。
     *
     * @param signType 签名类型，00-不签名，02-MD5
     */
    public void setSignType(String signType) {
        this.signType = signType;
    }

    /**
     * 读取 MD5 签名 key，仅 signType=02 时使用。
     *
     * @return MD5 签名 key
     */
    public String getSignKey() {
        return signKey;
    }

    /**
     * 设置 MD5 签名 key，仅 signType=02 时使用。
     *
     * @param signKey MD5 签名 key
     */
    public void setSignKey(String signKey) {
        this.signKey = signKey;
    }

    /**
     * 读取调用 ACC 的连接超时时间。
     *
     * @return 连接超时毫秒数
     */
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    /**
     * 设置调用 ACC 的连接超时时间。
     *
     * @param connectTimeoutMillis 连接超时毫秒数
     */
    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    /**
     * 读取调用 ACC 的读写超时时间。
     *
     * @return 读写超时毫秒数
     */
    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    /**
     * 设置调用 ACC 的读写超时时间。
     *
     * @param readTimeoutMillis 读写超时毫秒数
     */
    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }
}
