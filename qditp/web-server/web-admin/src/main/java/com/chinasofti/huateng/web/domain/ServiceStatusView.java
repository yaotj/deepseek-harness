package com.chinasofti.huateng.web.domain;

/**
 * 综管台服务监控的单服务探活结果（只读）。
 */
public class ServiceStatusView {
    /** 配置键名（如 gateTxnPay）。 */
    private String serviceName;
    /** 页面展示名。 */
    private String displayName;
    /** 探活目标（配置的服务地址 + /actuator/health）。 */
    private String healthUrl;
    /** UP / DOWN / UNKNOWN。 */
    private String status;
    /** 单次探活耗时（毫秒）；未发起或异常时为 -1。 */
    private long responseTimeMs = -1;
    /** 本次聚合检查的触发时间（yyyy-MM-dd HH:mm:ss）。 */
    private String checkedAt;
    /** 失败原因摘要（连接拒绝/超时/非 2xx 等），成功时为空。 */
    private String message;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getHealthUrl() {
        return healthUrl;
    }

    public void setHealthUrl(String healthUrl) {
        this.healthUrl = healthUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(String checkedAt) {
        this.checkedAt = checkedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
