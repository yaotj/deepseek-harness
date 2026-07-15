package com.chinasofti.huateng.alipay.paysign.model.request;

import lombok.Data;

/**
 * 支付宝出行-支付结果查询请求参数。
 */
@Data
public class AlipayTripPayQueryReqDTO {
    /**
     * 订单号
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
}
