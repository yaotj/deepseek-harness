package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code ALIPAY_PAY_TXN_DETAIL} 支付宝出行渠道支付交易明细实体。 */
@Data
public class AlipayPayTxnDetail {

    private Long id;

    private String orderNo;
    /** 交易类型：PAY 支付，REFUND 退款。 */
    private String payType;
    /** INIT / PROCESSING / SUCCESS / FAIL / RETRY / CLOSED。 */
    private String payStatus;

    private String thirdUserId;
    private String cardId;
    private String cardType;

    private String paymentVendor;
    private String payChannelCode;
    /** 我方签约流水号，取 ALIPAY_SIGN_INFO.AGREEMENT_CODE。 */
    private String requestSignSeq;
    /** 渠道协议号，取 ALIPAY_SIGN_INFO.CHANNEL_AGREEMENT_CODE；与上一字段不是同一个号。 */
    private String channelAgreementNo;

    /** 请求支付金额，单位分。 */
    private Integer amount;
    private Integer totalAmount;
    private Integer cashAmount;
    private Integer couponAmount;

    /** NONE / PROCESSING / PARTIAL / SUCCESS / FAIL。 */
    private String refundStatus;
    /** 已退总额（分），由 ALIPAY_REFUND_TXN_DETAIL 重算，NEVER 累加写入。 */
    private Integer refundAmount;
    private LocalDateTime lastRefundTime;

    private String merchantOrderNo;
    /** 支付中心侧支付订单号，退款报文的原支付订单号取此列。 */
    private String payCenterOrderNo;
    /** 渠道订单号，即支付宝交易号（旧表 TRADE_NO）。 */
    private String channelOrderNo;
    private String payUserId;

    private Integer requestCount;
    private LocalDateTime nextRequestTime;
    private LocalDateTime lastRequestTime;
    private LocalDateTime firstRequestTime;
    private LocalDateTime responseTime;
    /** 支付完成时间，回调原文 YYYYMMDDHH24MISS，String 是有意的。 */
    private String payTime;
    /** yyyyMMdd，月分区键 + 唯一键第二列。 */
    private String txnDate;

    private String scene;
    private String industryType;
    private String subject;
    private String body;
    private String authCode;
    private String notifyUrl;
    private String returnUrl;
    private String ipAddress;
    /** 订单超时，单位秒（旧表是字符串）。 */
    private Integer orderTimeOut;
    private String industryDetail;

    private String entryId;
    private String exitId;
    private String invoice;

    private String discountInfo;
    /** PROCESSING / SUCCESS / FAIL，MUST 与 payStatus 同步回写。 */
    private String debitRequestResult;
    private Integer discountFee;

    private String resultCode;
    /** 支付中心业务应答文案；NEVER 当 debitRequestResult 直接对外返回。 */
    private String resultMsg;
    private String remark;
    private String requestBody;
    private String responseBody;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
