package com.chinasofti.huateng.accsimulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ACC 模拟器配置属性，前缀 {@code acc-simulator}。
 *
 * <p>包含模拟 ACC 服务的连接参数与公共表单默认值，
 * 供 {@code AccSimulatorServiceImpl} 组装通知请求时使用。</p>
 */
@ConfigurationProperties(prefix = "acc-simulator")
public class AccSimulatorProperties {

    /**
     * FEP/ACC 服务的基础地址，未指定目标地址时作为默认通知地址的拼接前缀。
     */
    private String fepAccBaseUrl = "http://localhost:9110";

    /**
     * 服务提供方标识，默认 "06"。
     */
    private String providerId = "06";

    /**
     * 请求字符集，默认 UTF-8。
     */
    private String charset = "UTF-8";

    /**
     * 数据格式，默认 json。
     */
    private String format = "json";

    /**
     * 设备标识，默认 ACC-SIMULATOR。
     */
    private String deviceId = "ACC-SIMULATOR";

    /**
     * 签名类型，默认 "00"（不签名）。
     */
    private String signType = "00";

    /**
     * 历史记录保留条数上限。
     */
    private int historyLimit = 50;

    /**
     * 读取 FEP/ACC 服务基础地址。
     *
     * @return 基础地址，未指定目标地址时用于拼接默认通知 URL
     */
    public String getFepAccBaseUrl() {
        return fepAccBaseUrl;
    }

    /**
     * 设置 FEP/ACC 服务基础地址。
     *
     * @param fepAccBaseUrl 基础地址
     */
    public void setFepAccBaseUrl(String fepAccBaseUrl) {
        this.fepAccBaseUrl = fepAccBaseUrl;
    }

    /**
     * 读取服务提供方标识。
     *
     * @return 提供方标识
     */
    public String getProviderId() {
        return providerId;
    }

    /**
     * 设置服务提供方标识。
     *
     * @param providerId 提供方标识
     */
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    /**
     * 读取请求字符集。
     *
     * @return 字符集
     */
    public String getCharset() {
        return charset;
    }

    /**
     * 设置请求字符集。
     *
     * @param charset 字符集
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

    /**
     * 读取数据格式。
     *
     * @return 数据格式
     */
    public String getFormat() {
        return format;
    }

    /**
     * 设置数据格式。
     *
     * @param format 数据格式
     */
    public void setFormat(String format) {
        this.format = format;
    }

    /**
     * 读取设备标识。
     *
     * @return 设备标识
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 设置设备标识。
     *
     * @param deviceId 设备标识
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * 读取签名类型。
     *
     * @return 签名类型
     */
    public String getSignType() {
        return signType;
    }

    /**
     * 设置签名类型。
     *
     * @param signType 签名类型
     */
    public void setSignType(String signType) {
        this.signType = signType;
    }

    /**
     * 读取历史记录保留条数上限。
     *
     * @return 历史记录上限
     */
    public int getHistoryLimit() {
        return historyLimit;
    }

    /**
     * 设置历史记录保留条数上限。
     *
     * @param historyLimit 历史记录上限
     */
    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }
}
