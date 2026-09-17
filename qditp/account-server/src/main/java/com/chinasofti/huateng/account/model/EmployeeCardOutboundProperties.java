package com.chinasofti.huateng.account.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 员工码出网配置（前缀 {@code employee-card}），供 {@code EmployeeCardOutboundServiceImpl} 使用。
 */
@Component
@ConfigurationProperties(prefix = "employee-card")
public class EmployeeCardOutboundProperties {
    /**
     * APP 员工码批量注册地址。
     */
    private String appRegisterUrl = "";

    /**
     * ACC 员工码资料查询地址。
     */
    private String accQueryUrl = "";

    /**
     * ACC 员工码激活 / 禁用地址。
     */
    private String accActivateUrl = "";

    /**
     * 出网报文的服务提供方标识。
     */
    private String providerId = "06";

    /**
     * 出网报文字符集。
     */
    private String charset = "UTF-8";

    /**
     * 出网报文格式。
     */
    private String format = "json";

    /**
     * 出网报文设备标识。
     */
    private String deviceId = "ITP";

    /**
     * 出网报文签名类型；{@code 00} 表示免签。
     */
    private String signType = "00";

    /**
     * 连接超时毫秒数。
     */
    private int connectTimeoutMs = 3000;

    /**
     * 读超时毫秒数。
     */
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
