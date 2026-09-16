package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code ALIPAY_PAY_TXN_DETAIL} 支付宝出行渠道支付交易明细实体。
 *
 * <p>形态对齐 pay-sign-server 的 {@code PayTxnDetail}，与本模块旧的 {@link AlipayPayLog}
 * 有三处**刻意**不同，改动前 MUST 先读懂为什么：</p>
 * <ol>
 *   <li><b>金额一律 {@code Integer}、单位分</b>。旧实体 31 个字段全是 String，
 *       退款汇总因此要在 SQL 里 {@code TO_NUMBER} 求和再 {@code TO_CHAR} 回写。
 *       <b>NEVER 把这里改回 String</b>。</li>
 *   <li><b>时间分两类</b>：{@code createTime} / {@code updateTime} / {@code *RequestTime}
 *       等是 {@code LocalDateTime}（库里 TIMESTAMP）；而 {@code payTime} 保持 String，
 *       因为它是<b>支付中心回调原文</b>（{@code YYYYMMDDHH24MISS}），落库即证据、不做解析。
 *       旧实体的 {@code transTime} 是 String 且存量格式不统一（既有 {@code 2026-07-28 16:59:55}
 *       也有毫秒时间戳），本实体不保留该字段，按时间过滤 MUST 用 {@code createTime}。</li>
 *   <li><b>主键是序列生成的 {@code id}</b>，业务唯一键是 {@code (orderNo, txnDate)}
 *       对应唯一索引 {@code UK_APTD_ORDER}。旧实体的 UUID 主键 {@code paySeq} 不再保留 ——
 *       它既排不出时序，也拦不住重复落单。</li>
 * </ol>
 *
 * <p>{@code payCenterOrderNo} 与 {@code merchantOrderNo} 不是一回事：后者是我方商户订单号、
 * 等同 {@code orderNo}；前者是支付中心侧的支付订单号，<b>退款报文的「原支付订单号」MUST 用它</b>，
 * 缺它退款必失败。</p>
 *
 * <p>{@code debitRequestResult} 的值域是 {@code PROCESSING/SUCCESS/FAIL}，与对外契约字段
 * {@code debitRequestResult} 的 {@code 0}/{@code 1} 值域<b>同名不同义，NEVER 混用</b>。</p>
 */
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
