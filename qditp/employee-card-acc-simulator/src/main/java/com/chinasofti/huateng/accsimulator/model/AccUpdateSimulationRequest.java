package com.chinasofti.huateng.accsimulator.model;

import com.chinasofti.huateng.model.employee.EmployeeInfoUpdateNotifyReqDTO;

/**
 * 资料变更通知模拟请求。
 *
 * <p>管理端通过该对象向 FEP/ACC 发送员工资料变更通知，
 * 可同时指定目标地址和公共表单参数。</p>
 */
public class AccUpdateSimulationRequest {

    /**
     * 目标地址，为空时使用配置中的默认地址拼接 {@code /employee_card/update_notify}。
     */
    private String targetUrl;

    /**
     * 员工资料变更信息，作为 bizData 发送。
     */
    private EmployeeInfoUpdateNotifyReqDTO employee;

    /**
     * 服务提供方标识，为空时使用配置默认值。
     */
    private String providerId;

    /**
     * 请求字符集，为空时使用配置默认值。
     */
    private String charset;

    /**
     * 数据格式，为空时使用配置默认值。
     */
    private String format;

    /**
     * 设备标识，为空时使用配置默认值。
     */
    private String deviceId;

    /**
     * 签名类型，为空时使用配置默认值。
     */
    private String signType;

    /**
     * 签名值。
     */
    private String sign;

    /**
     * 读取目标地址。
     *
     * @return 目标地址，为空时使用默认地址
     */
    public String getTargetUrl() {
        return targetUrl;
    }

    /**
     * 设置目标地址。
     *
     * @param targetUrl 目标地址
     */
    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    /**
     * 读取员工资料变更信息。
     *
     * @return 员工资料变更信息
     */
    public EmployeeInfoUpdateNotifyReqDTO getEmployee() {
        return employee;
    }

    /**
     * 设置员工资料变更信息。
     *
     * @param employee 员工资料变更信息
     */
    public void setEmployee(EmployeeInfoUpdateNotifyReqDTO employee) {
        this.employee = employee;
    }

    /**
     * 读取服务提供方标识。
     *
     * @return 服务提供方标识
     */
    public String getProviderId() {
        return providerId;
    }

    /**
     * 设置服务提供方标识。
     *
     * @param providerId 服务提供方标识
     */
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    /**
     * 读取请求字符集。
     *
     * @return 请求字符集
     */
    public String getCharset() {
        return charset;
    }

    /**
     * 设置请求字符集。
     *
     * @param charset 请求字符集
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
     * 读取签名值。
     *
     * @return 签名值
     */
    public String getSign() {
        return sign;
    }

    /**
     * 设置签名值。
     *
     * @param sign 签名值
     */
    public void setSign(String sign) {
        this.sign = sign;
    }
}
