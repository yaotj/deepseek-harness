package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code ALIPAY_PAY_CALLBACK_LOG} 支付宝出行支付结果回调凭据（支付中心 → 我方）。
 *
 * <p>本表是<b>追加型事实流水</b>：一次推送落一行，重推各存一份，刻意没有唯一索引 —— 形态照
 * pay-sign 的 {@code PAY_CALLBACK_LOG}。它回答「对方第 N 次推送时说了什么、我处理成没成」。
 * 「这一单当前什么状态」在 {@code ALIPAY_PAY_TXN_DETAIL}，「订单成不成立」在
 * {@code GATE_TXN_PAY}，「我方出网说了什么」在 {@code ALIPAY_PAY_CENTER_MSG_LOG}。四者 NEVER 混。
 *
 * <p><b>字段只有这么少是核对过的结论，不是漏写。</b>2026-09-18 逐字段核对代码与供方文档：支付宝
 * 出行的回调 DTO（{@code model} 的 {@code AlipayTripPayNotifyReqDTO}）<b>只有 6 个业务字段</b> ——
 * {@code orderNo} / {@code channelVoucherId} / {@code transAmount} / {@code transTime} /
 * {@code transStatus} / {@code cardNo}。因此 {@code cashAmount} / {@code couponAmount} /
 * {@code discountFee} / {@code discountInfo} / {@code payUserId} / {@code merchantOrderNo} /
 * {@code payTime} / {@code channelOrderNo} 这 8 个字段<b>在这条链路里没有入向来源</b>，一个都没建。
 * pay-sign 的 {@code ReceivePayResultReqDTO} 有 12 个字段是<b>另一条契约</b>，
 * <b>NEVER 因为那边有就照抄过来</b>；哪天支付中心扩了契约，MUST 按当时的真实报文加列。
 *
 * <p>支付方向补进来的两个是 {@link #totalAmount}（{@code payQuery} 同步应答里取过的那个键，
 * 与支付宝报文原文 {@link #transAmount} <b>不同口径、NEVER 合并</b>）与 {@link #txnDate}（账期检索用，
 * 本表当前未分区）。
 *
 * <p><b>退款方向另有三列</b>：{@link #refundOrderNo} / {@link #refundAmount} / {@link #refundStatus}，
 * 只在 {@code CALLBACK_TYPE=REFUND} 的行有值，来自契约 §5.2 退款结果回调。它们与支付方向的列
 * <b>互不覆盖</b>：同一张表按 {@code CALLBACK_TYPE} 分两组，取哪一组由该列决定，
 * <b>NEVER 把退款金额写进 {@link #totalAmount}</b>。
 */
@Data
public class AlipayPayCallbackLog {

    /** 回调流水号，本服务生成的去横线 UUID。 */
    private String callbackSeq;
    /** 我方订单号，与 {@code GATE_TXN_PAY.ORDER_NO} 同源；同时是硬限次计数键。 */
    private String orderNo;
    /**
     * 回调类型：{@code PAY} 支付结果回调，{@code REFUND} 退款结果回调。
     *
     * <p>它与 {@link #orderNo} 一起构成硬限次的计数键，改动前 MUST 想清对计数的影响 —— 两个方向共用一张表，
     * 靠本列分开计数，混值会让支付侧的重推上限被退款回调顶掉。</p>
     */
    private String callbackType;

    /** 支付宝报文原始交易状态，1 成功 2 失败。映射后的 SUCCESS / FAIL 是派生值，<b>刻意不落库</b>。 */
    private String transStatus;
    /** 支付渠道订单号（支付宝报文口径）。 */
    private String channelVoucherId;
    /** 支付宝报文里的交易金额原文（字符串，原样直存）；与 {@link #totalAmount} 不同口径。 */
    private String transAmount;
    /** 支付宝报文里的交易时间原文 {@code yyyy-MM-dd HH:mm:ss}。 */
    private String transTime;

    /** 支付中心口径的订单总金额（分），来自 {@code payQuery} 同步应答；与 {@link #transAmount} NEVER 合并。 */
    private Integer totalAmount;
    /** 订单日期 yyyyMMdd，取自主表；供按账期检索，本表当前未分区。 */
    private String txnDate;

    /**
     * 退款单号，取回调的 {@code outRefundNo}（商户退款流水号），与 {@code ALIPAY_REFUND_LOG.REFUND_ORDER_NO} 同源。
     *
     * <p>只在 {@code CALLBACK_TYPE=REFUND} 的行有值。<b>NEVER 改成填 {@code refundNo}</b> —— 那是支付中心侧
     * 自己的退款流水号，与我方退款单对不上，用它做关联会串单；它只在 {@link #rawBody} 里留证。
     * 一笔订单可多次退款，缺这一列时 REFUND 行分不清是哪笔退款。</p>
     */
    private String refundOrderNo;
    /** 退款金额（分），回调回传值原样解析；报文送的是字符串，解析不出时留 NULL 而不打断留痕。 */
    private Integer refundAmount;
    /** 退款状态，回调 {@code refundResult} 原值（SUCCESS / FAIL / PROCESSING）；当前只落证据、不回写业务。 */
    private String refundStatus;

    /** 整包报文原文（列已是 CLOB，接线时不必再截断）。 */
    private String rawBody;
    /** 本次处理结果 SUCCESS / FAIL / MANUAL，MANUAL 需运维巡检。 */
    private String handleStatus;
    /** 处理结果说明，失败原因截断 500 字。 */
    private String handleMsg;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
