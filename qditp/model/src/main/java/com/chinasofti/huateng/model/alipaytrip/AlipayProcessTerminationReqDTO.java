package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行销卡批处理请求。
 *
 * <p>调用方通常是 web-server 的 Quartz 任务（每天 2 点）。两个字段都可以不传：
 * 不传 referenceTime 时不按登记时间过滤，扫全部 PENDING。</p>
 *
 * <p>referenceTime 只接受 {@code yyyyMMdd}（按当天 00:00:00 解析）或 {@code yyyyMMddHHmmss}。
 * 传入后只处理 {@code CREATE_TIME <= referenceTime} 的登记记录——
 * {@code ALIPAY_TERMINATION_REQUEST} 没有专用的「申请时间」列，CREATE_TIME 就是申请时间。</p>
 */
public class AlipayProcessTerminationReqDTO {

    /** 基准时间，yyyyMMdd 或 yyyyMMddHHmmss；为空表示不按登记时间过滤。 */
    private String referenceTime;

    public String getReferenceTime() {
        return referenceTime;
    }

    public void setReferenceTime(String referenceTime) {
        this.referenceTime = referenceTime;
    }

    @Override
    public String toString() {
        return "AlipayProcessTerminationReqDTO{referenceTime='" + referenceTime + "'}";
    }
}
