package com.chinasofti.huateng.collectpay.controller.page;

/**
 * 运营端 APP 取票订单「指定金额退款」请求体。
 *
 * <p>只有金额一个字段：退款原因不开放填写 —— {@code AppOrderServiceImpl.doRefund} 把
 * {@code REFUND_REASON} 硬编码为「业务操作失败」，接收一个会被丢掉的原因字段属于骗调用方。
 * 要支持自定义原因得改 {@code doRefund} 签名，那是旧链路、本次不动。</p>
 */
public class AppPartialRefundRequest {

    /** 本次退款金额，单位**分**，正整数。MUST 不超过可退余额，超了会被服务层拒。 */
    private Integer refundAmount;

    public Integer getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Integer refundAmount) {
        this.refundAmount = refundAmount;
    }
}
