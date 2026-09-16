package com.chinasofti.huateng.model.app;

/**
 * IF8A-35 查询用户账务信息请求。
 *
 * <p>规格只有一个业务字段 thirdUserId，统计范围是该用户在 {@code GATE_TXN_PAY} 上的
 * 出站扣费订单（后付费），不含支付宝渠道的 {@code ALIPAY_PAY_LOG}——APP 用户不走支付宝渠道，
 * 该口径 2026-09-08 已与业务确认。</p>
 */
public class RequestUserAccInfoReqDTO {

    /** 第三方用户 id。 */
    private String thirdUserId;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    @Override
    public String toString() {
        return "RequestUserAccInfoReqDTO{thirdUserId='" + thirdUserId + "'}";
    }
}
