package com.chinasofti.huateng.model.paysign;

/**
 * 解约申请批处理入参（内部接口 /internal/termination/process）。
 *
 * <p>两个字段都可省略：**都不传时行为与历史一致**——不按申请时间过滤，扫全部
 * PENDING + SCANNING。传了则只处理「申请时间早于截止点」的记录：
 * {@code cutoff = referenceTime - delayDays}。</p>
 *
 * <p>业务口径「解约申请 4 天后才确认」由 delayDays 表达：referenceTime 传今天，
 * 捞到的是 4 天前及更早的申请；referenceTime 传 4 天后的日期，捞到的是今天的申请。
 * 过滤同时作用于 PENDING 与 SCANNING —— 口径是「截止点之前尚未成功的都在扫描范围内」。</p>
 */
public class ProcessTerminationReqDTO {

    /**
     * 基准时间，格式 {@code yyyyMMdd}（当天 00:00:00）或 {@code yyyyMMddHHmmss}。
     * 省略时取当前时间。
     */
    private String referenceTime;

    /**
     * 规定天数，基准时间往前推的天数。省略时取 pay-sign-server 配置
     * {@code termination.confirm-delay-days}（默认 4）。传 0 表示不留延迟。
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
