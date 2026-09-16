package com.chinasofti.huateng.account.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 员工码出网配置（前缀 {@code employee-card}），供 {@code EmployeeCardOutboundServiceImpl} 使用。
 *
 * <p>2026-09-11 由 10 个散落的 {@code @Value}（8 个报文/地址项 + 2 个超时）收拢成一个对象。
 * <b>配置键一个字都没改</b>，因此 K8s Deployment 的 env 与 `application.properties` 都不用动。</p>
 *
 * <p><b>这是全仓第一处 {@code @ConfigurationProperties}</b>（改造前全项目零使用，一律 {@code @Value}）。
 * 它是 Spring Boot 原生能力、没引入新依赖；若团队不接受这种写法，回退方式是把字段改回
 * {@code @Value} 逐个注入，<b>NEVER 为了回退去改配置键名</b>。</p>
 *
 * <p>注意本前缀下还有 {@code employee-card.app-batch-size}，它<b>刻意不在本类里</b> ——
 * 那是 {@code EmployeeCardServiceImpl} 的批次切分策略，不属于「出网」这件事（见 ADR-D18）。
 * 未知字段默认被忽略，因此不会因为少了它而绑定失败。</p>
 */
@Component
@ConfigurationProperties(prefix = "employee-card")
public class EmployeeCardOutboundProperties {

    /** APP 员工码批量注册地址。为空即视为「未配置」，出网方法会直接返回失败而不发请求。 */
    private String appRegisterUrl = "";

    /** ACC 员工码资料查询地址。为空即视为「未配置」。 */
    private String accQueryUrl = "";

    /** ACC 员工码激活 / 禁用地址。为空即视为「未配置」，激活入口会返 9999。 */
    private String accActivateUrl = "";

    /** 出网报文的服务提供方标识。 */
    private String providerId = "06";

    /** 出网报文字符集。 */
    private String charset = "UTF-8";

    /** 出网报文格式。 */
    private String format = "json";

    /** 出网报文设备标识。 */
    private String deviceId = "ITP";

    /** 出网报文签名类型；{@code 00} 表示免签。 */
    private String signType = "00";

    /** 连接超时毫秒数。 */
    private int connectTimeoutMs = 3000;

    /** 读超时毫秒数。 */
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
