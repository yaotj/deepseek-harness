package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code ALIPAY_REFUND_TXN_DETAIL} 支付宝出行渠道退款明细实体。
 *
 * <p>形态对齐 pay-sign-server 的 {@code PayRefundDetail}。本表是<b>退款账本的唯一真源</b>：
 * {@code ALIPAY_PAY_TXN_DETAIL.REFUND_AMOUNT / REFUND_STATUS} 由
 * {@code AlipayPayTxnDetailMapper.updateRefundSummary} 按本表重算得出，
 * <b>NEVER 由调用方传增量累加</b>（入参没有幂等键，执行两次就多记一笔）。</p>
 *
 * <p>与旧 {@link AlipayRefundLog} 的差异：金额改 {@code Integer}（分）；
 * 有 {@code (refundOrderNo, txnDate)} 唯一索引；补了 {@code requestCount} /
 * {@code nextRequestTime} 以支撑退款回查补偿；<b>不再有 {@code deleteFlag}</b> ——
 * 退款明细不该被逻辑删除，且旧表每条查询都得记着带 {@code DELETE_FLAG='0'}，
 * 漏一次就把已删行算进汇总。</p>
 *
 * <p>{@code txnDate} 是<b>本次退款的发起日期</b>，与原支付单的 {@code txnDate}
 * 各自独立、<b>NEVER 复用原单日期</b>：两者是各自独立的事件，跨零点退款时会分叉。</p>
 */
@Data
public class AlipayRefundTxnDetail {

    private Long id;

    /** 内部退款单号，由本模块生成。 */
    private String refundOrderNo;
    /** 原支付订单号，对应 ALIPAY_PAY_TXN_DETAIL.ORDER_NO。 */
    private String orderNo;
    /**
     * INIT / PROCESSING / SUCCESS / FAIL / RETRY / CLOSED。
     *
     * <p>拿不到支付中心业务应答时 MUST 保持 {@code PROCESSING}，<b>NEVER 置 FAIL</b>：
     * 置 FAIL 会让汇总少记已退金额，同一笔随后能再退一次。</p>
     */
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
