package com.chinasofti.huateng.model.paysign;

/**
 * 解约申请批处理入参（内部接口 /internal/termination/process）。
 */
public class ProcessTerminationReqDTO {

    /**
     * 基准时间，格式 {@code yyyyMMdd}（当天 00:00:00）或 {@code yyyyMMddHHmmss}。
     */
    private String referenceTime;

    /**
     * 规定天数，基准时间往前推的天数。省略时取 pay-sign-server 配置。
     */
    private Integer delayDays;

    public String getReferenceTime() {
        return referenceTime;
    }

    public void setReferenceTime(String referenceTime) {
        this.referenceTime = referenceTime;
    }

    public Integer getDelayDays() {
        return delayDays;
    }

    public void setDelayDays(Integer delayDays) {
        this.delayDays = delayDays;
    }

    @Override
    public String toString() {
        return "ProcessTerminationReqDTO{referenceTime='" + referenceTime + "', delayDays=" + delayDays + '}';
    }
}
