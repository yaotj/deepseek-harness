package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行销卡批处理请求。
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
