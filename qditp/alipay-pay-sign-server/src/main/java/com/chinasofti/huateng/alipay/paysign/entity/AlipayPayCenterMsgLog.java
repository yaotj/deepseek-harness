package com.chinasofti.huateng.alipay.paysign.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code ALIPAY_PAY_CENTER_MSG_LOG} 我方 → 支付中心的出网报文留痕，<b>一次调用一行</b>。
 *
 * <p>覆盖四个接口：{@code requestPay}（扣款）、{@code payQuery}（支付结果查询）、
 * {@code requestRefund}（退款申请）、{@code refundQuery}（退款结果查询），用 {@link #apiName} 区分。
 *
 * <p><b>为什么单独一张表</b>：这些报文原来是覆盖式写在支付明细表的 {@code RESPONSE_BODY} 上，
 * <b>重试三次只剩最后一次</b>，前两次的证据永久丢失 —— 而这类记录存在的唯一意义就是留证据。
 * 改成一次调用一行后，重试链路的每一步都可举证，当前态表也不再带 CLOB。
 *
 * <p><b>刻意是追加型流水、没有唯一索引</b>：同一个 {@link #orderNo} 会有多行（首次 + 每次重试 +
 * 每次回查）。判断「这一单当前什么状态」MUST 查 {@code ALIPAY_PAY_TXN_DETAIL}，
 * <b>NEVER 在本表上按时间取最后一行推断状态</b> —— 最后一行可能是一次超时的 {@code payQuery}，
 * 与订单实际状态无关。
 *
 * <p><b>NEVER 让业务逻辑读本实体的字段</b>：它只服务排查与对账举证。任何被业务判断依赖的值
 * MUST 落到当前态表的列上，否则就会出现「靠翻流水拼状态」这种无法维护的读法。
 *
 * <p>与 {@code ALIPAY_PAY_CALLBACK_LOG} 是对称的两半：那张表是「对方推给我方」，本表是
 * 「我方打给对方」。{@link #responseBody} 是<b>同步应答</b>原文，异步回调原文在那张表的
 * {@code RAW_BODY} 里，两者不是一回事。
 */
@Data
public class AlipayPayCenterMsgLog {

    private Long id;

    /** 地铁侧订单号，等于 {@code GATE_TXN_PAY.ORDER_NO} 与 {@code ALIPAY_PAY_TXN_DETAIL.ORDER_NO}。 */
    private String orderNo;
    /** yyyyMMdd，月分区键；取自主表，NEVER 用本地当天日期另算。 */
    private String txnDate;
    /** requestPay / payQuery / requestRefund / refundQuery。 */
    private String apiName;
    /** 本次请求的从键：退款类接口填退款单号，支付类接口留空。 */
    private String requestNo;

    private String scene;
    /** 行业类型：1 地铁 / 2 公交 / 3 打车 / 4 购物。 */
    private String industryType;
    private String ipAddress;

    /** 支付中心本次同步应答的业务码。 */
    private String retCode;
    /** 支付中心本次同步应答文案；NEVER 当 debitRequestResult 直接对外返回。 */
    private String retMsg;
    /** 本次调用耗时毫秒，用于定位支付中心慢响应。 */
    private Long elapsedMs;

    /** 我方送出的请求报文原文，MUST 写。 */
    private String requestBody;
    /** 支付中心<b>同步应答</b>原文；异步回调原文在 {@code ALIPAY_PAY_CALLBACK_LOG.RAW_BODY}。 */
    private String responseBody;
    private String remark;

    private LocalDateTime createTime;
}
