package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code ALIPAY_REFUND_TXN_DETAIL} 支付宝出行渠道退款明细 —— <b>只装当前态，18 列、无 CLOB</b>，
 * 与 {@link AlipayPayTxnDetail} 同一套边界口径。
 *
 * <p><b>2026-09-20 按库内实际列收窄，删掉的 10 个字段 NEVER 加回</b>：此前实体与 mapper 有 28 个字段，
 * 而 {@code AFCITPDB} 里这张表<b>只有 18 列</b>（实测），多出来的那 10 个在 DDL 里从来没有过 ——
 * {@code insert} / {@code updateRequestResult} / 三条 {@code select} 一旦被调用就是 {@code ORA-00904}。
 * 之所以线上没炸，是因为<b>这张表当时零业务调用方</b>（退款链路仍走旧表 {@code ALIPAY_REFUND_LOG}），
 * 属「代码在用、库里没有」的潜伏形态。接线退款前 MUST 以本实体的 18 个字段为准，
 * <b>NEVER 照旧实体 {@code AlipayRefundLog} 的字段补列</b>。
 * <ul>
 *   <li>{@code thirdUserId} / {@code cardId} / {@code cardIssueCode} —— 主体维度，权威在
 *       {@code GATE_TXN_PAY}（按 {@link #orderNo} 回查），本表存第二份就是两处口径。</li>
 *   <li>{@code retCode} / {@code retMsg} / {@code payCenterCode} / {@code payCenterMsg} /
 *       {@code ipAddress} / {@code requestBody} / {@code responseBody} —— 报文与应答码留痕，
 *       全部归 {@code ALIPAY_PAY_CENTER_MSG_LOG}（{@code API_NAME} 取 {@code requestRefund} /
 *       {@code refundQuery}、{@code REQUEST_NO} 填退款单号）。**一次调用一行**，而放在本表上是
 *       覆盖式写入、重试三次只剩最后一次报文 —— 那恰好毁掉留证据这个唯一目的。</li>
 * </ul>
 * <b>已知未闭合</b>：退款方向目前还没有人调 {@code AlipayPayCenterMsgLogWriter}（实测两个调用点的
 * {@code apiName} 全是 {@code requestPay}），因此退款报文当前<b>零留痕</b>；接线退款时 MUST 一并补上，
 * 否则这 7 个字段既不在本表、也不在流水表，等于彻底没有证据。
 */
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

    private String operator;
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
