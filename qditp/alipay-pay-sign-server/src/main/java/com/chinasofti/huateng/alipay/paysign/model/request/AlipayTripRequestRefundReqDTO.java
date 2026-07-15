package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;

/**
 * 支付宝出行-退款申请请求参数。
 */
@Data
public class AlipayTripRequestRefundReqDTO {
    /**
     * 原订单号
     */
    private String orderNo;

    /**
     * 卡机构编号，支付宝0007
     */
    private String cardIssueCode;

    /**
     * 逻辑卡号
     */
    private String cardNum;

    /**
     * 渠道协议号
     */
    private String channelAgreementNo;

    /**
     * 退款金额，单位分
     */
    private String refundAmount;

    /**
     * 退款订单号
     */
    private String refundOrderNo;
}
