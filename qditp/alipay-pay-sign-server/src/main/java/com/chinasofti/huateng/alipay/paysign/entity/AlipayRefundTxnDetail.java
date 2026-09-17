package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code ALIPAY_REFUND_TXN_DETAIL} 支付宝出行渠道退款明细实体。 */
@Data
public class AlipayRefundTxnDetail {

    private Long id;

    /** 内部退款单号，由本模块生成。 */
    private String refundOrderNo;
    /** 原支付订单号，对应 ALIPAY_PAY_TXN_DETAIL.ORDER_NO。 */
    private String orderNo;
    /** INIT / PROCESSING / SUCCESS / FAIL / RETRY / CLOSED。 */
    private String refundStatus;

    /** 退款金额，单位分。 */
    private Integer refundAmount;
    private String refundReason;

    private String thirdUserId;
    private String cardId;
    private String cardIssueCode;

    private String merchantRefundNo;
    private String refundNo;
    private String channelRefundNo;

    private Integer requestCount;
    private LocalDateTime nextRequestTime;
    private LocalDateTime lastRequestTime;

    /** 退款完成时间，渠道原文直存。 */
    private String refundTime;
    /** yyyyMMdd，本次退款发起日期，月分区键 + 唯一键第二列。 */
    private String txnDate;

    /** 我方对外应答码。 */
    private String retCode;
    private String retMsg;
    /** 支付中心应答码，与 retCode 分列存放。 */
    private String payCenterCode;
    private String payCenterMsg;

    private String operator;
    private String ipAddress;
    private String remark;

    private String requestBody;
    private String responseBody;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
