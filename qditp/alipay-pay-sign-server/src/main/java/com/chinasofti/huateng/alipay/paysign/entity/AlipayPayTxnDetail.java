package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code ALIPAY_PAY_TXN_DETAIL} 支付宝出行（小程序）渠道支付交易明细 —— <b>只装当前态，19 列、无 CLOB</b>。
 *
 * <p>本实体回答的只有一件事：「这笔 {@code GATE_TXN_PAY} 订单在支付中心侧<b>现在</b>是什么状态、
 * 已退多少」。按 {@code orderNo} 一对一挂在主表上。订单是否成立、扣费到底成没成，权威在
 * {@code GATE_TXN_PAY.DEBIT_STATUS}，{@link #payStatus} 只表达支付侧结果。
 *
 * <p>与它配套的另外两张表 —— 职责 NEVER 混：
 * <ul>
 *   <li>{@code ALIPAY_PAY_CENTER_MSG_LOG}：我方 → 支付中心，<b>一次调用一行</b>。请求与应答原文、
 *       应答码、耗时、场景、行业类型、IP 全在那里。</li>
 *   <li>{@code ALIPAY_PAY_CALLBACK_LOG}：支付中心 → 我方，<b>一次推送一行</b>。</li>
 * </ul>
 * 报文原文之所以不留在本实体：原来 {@code RESPONSE_BODY} 是覆盖式写入，<b>重试三次只剩最后一次
 * 报文</b>，前两次的证据永久丢失 —— 而那两列存在的唯一意义就是留证据。
 *
 * <p><b>本表不是查询入口。</b>行程列表与详情统一是「先查 {@code GATE_TXN_PAY} 拿主体信息（分页 /
 * 过滤 / 排序都在主表侧，走 {@code /ci/gateTxnPay/app/requestTransList} 与 {@code queryByOrderNo}）
 * → 再按拿到的 {@code orderNo} 来本表取支付侧详情」。因此本实体<b>没有</b>用户、卡号、卡类型、
 * 进出站 ID 这些维度 —— 主表都有，在这里再存一份就是两处口径。
 *
 * <p>金额只有 {@link #amount} 一个（我方送出的请求金额，也是退款可退上限）。对端回传的
 * {@code cashAmount} / {@code couponAmount} / {@code discountFee} / {@code discountInfo} /
 * {@code payUserId} / {@code merchantOrderNo} <b>在支付宝出行链路里没有入向来源</b>（回调 DTO 只有
 * 6 个字段），因此一列都没建。**NEVER 因为 pay-sign 的 {@code ReceivePayResultReqDTO} 有这些字段
 * 就照抄过来** —— 那是另一条契约。完整删除清单与理由见 {@code sql/alipay-pay-txn-schema.sql}。
 *
 * <p>两处刻意的类型选择，NEVER 改回去：① 金额用 {@code Integer} 且单位是分，旧
 * {@code AlipayPayLog} 那套 String 金额查询侧只能 {@code TO_NUMBER}；② 主键是序列生成的
 * {@link #id}，业务唯一键是 {@code (orderNo, txnDate)}，旧表的 UUID 主键既排不出时序也拦不住
 * 重复落单。
 *
 * <p><b>字符串时间字段只有 {@link #transTime} 一个</b>（2026-09-18 加入，见
 * {@code sql/alipay-pay-txn-detail-trans-time-migration.sql}）：它是支付宝出行记录应答里
 * {@code payOrderNoDate} 的取值来源，旧实现取的是 {@code ALIPAY_PAY_LOG.TRANS_TIME}，同一个值。
 * 本表重建时与它一起被删掉的 {@code transAmount} / {@code transStatus} / {@code cardNo}
 * <b>仍然 NEVER 加回</b> —— 金额权威是 {@link #amount}，状态权威是 {@link #payStatus} 与
 * {@code GATE_TXN_PAY.DEBIT_STATUS}，卡号在主表。加回来一个不等于把那一批都加回来。
 */
@Data
public class AlipayPayTxnDetail {

    private Long id;

    /** 地铁侧订单号，等于 {@code GATE_TXN_PAY.ORDER_NO}；本表唯一的对外查询键。 */
    private String orderNo;
    /** yyyyMMdd，月分区键 + 唯一键第二列；取主表 {@code TXN_DATE}，NEVER 用本地当天日期另算。 */
    private String txnDate;

    /**
     * 支付侧状态：INIT / PROCESSING / SUCCESS / FAIL / RETRY / CLOSED。
     *
     * <p>本表<b>只有这一个状态列</b>。原先并存的 {@code DEBIT_REQUEST_RESULT} 已于 2026-09-18 删除、
     * NEVER 加回 —— 它是本字段的派生值，而对外契约那个 {@code debitRequestResult}（0/1）实际由主表
     * {@code GATE_TXN_PAY.DEBIT_STATUS} 映射。两个状态列并存只会带来「MUST 同步回写、漏写就静默
     * 不一致」这条本不必存在的护栏。
     */
    private String payStatus;

    /** 我方送出的请求支付金额（分），恒有值；本表唯一的金额字段，退款可退上限只取它。 */
    private Integer amount;

    /** NONE / PROCESSING / PARTIAL / SUCCESS / FAIL。 */
    private String refundStatus;
    /** 已退总额（分），由 {@code ALIPAY_REFUND_TXN_DETAIL} 重算，NEVER 累加写入。 */
    private Integer refundAmount;
    private LocalDateTime lastRefundTime;

    /** 支付中心侧支付订单号（来自同步应答），退款报文的原支付订单号取此字段，缺它退款必失败。 */
    private String payCenterOrderNo;
    /** 渠道订单号，即支付宝交易号（旧表 TRADE_NO）；属当前态关键标识，故留在本表。 */
    private String channelOrderNo;

    /**
     * 支付时刻，支付中心回调报文的 {@code transTime} <b>原文直存不解析</b>。
     *
     * <p>格式不统一：实测既有 {@code 2026-09-18 15:49:30} 也有 {@code 20260918021500}。因此
     * <b>NEVER 改成 {@code LocalDateTime}</b>，也 NEVER 在查询里 {@code TO_DATE(TRANS_TIME, ...)}
     * —— 既走不到索引，遇到另一种格式还会抛 {@code ORA-01861}。要按时间筛选一律去主表用
     * {@code GATE_TXN_PAY.TXN_DATE} / {@code OUT_TIME}。
     *
     * <p>唯一写入方是支付回调（{@code updatePayCallback} 的 SET 段，套 NVL 不覆盖已有值）。落单时
     * 恒为 null —— 那一刻对端还没告诉我们支付时刻；payQuery 方向也不写它。
     */
    private String transTime;

    /** 我方签约流水号，取 {@code ALIPAY_SIGN_INFO.AGREEMENT_CODE}。 */
    private String requestSignSeq;
    /** 渠道协议号，取 {@code ALIPAY_SIGN_INFO.CHANNEL_AGREEMENT_CODE}；与上一字段不是同一个号。 */
    private String channelAgreementNo;

    /** 已发起支付请求次数；每次出网的报文原文在 {@code ALIPAY_PAY_CENTER_MSG_LOG}，一次一行。 */
    private Integer requestCount;
    private LocalDateTime lastRequestTime;
    private LocalDateTime nextRequestTime;

    /** 发票状态；旧表实测从未被写过、本表也没有写入方，属占位字段，NEVER 假定它有值。 */
    private String invoice;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
