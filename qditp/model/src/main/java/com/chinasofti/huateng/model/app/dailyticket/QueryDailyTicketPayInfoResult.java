package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 按日票票号查购票支付信息的应答（服务间内部 DTO，非 APP 对外契约）。
 *
 * <p>三个字段一一对应 IF8A-05 / IF8A-34 出参 {@code ticketTransRecord} 里的同名字段
 * （甲方规格 {@code 青岛地铁-ITP与APP接口规范R6.docx} 的 {@code if8a_34 – 获取订单详情}），
 * 因此**字段名 MUST 与那三个保持一致**，调用方直接对填、不做二次改名。</p>
 *
 * <p>数据来源（2026-09-15 用票号 {@code 2099439054066438144} 实测打通）：
 * {@code DAILY_TICKET_INSTANCE}（按 {@code TICKET_CODE}）→ {@code ORDER_NO} →
 * {@code DAILY_TICKET_ORDER} 的 {@code TRADE_NO} / {@code PAY_DATE} / {@code PAY_CHANNEL_CODE}。</p>
 *
 * <p><b>{@code payOrderNoDate} 装的是「购票付款时刻」，不是本次行程的扣款时刻</b> ——
 * 日票那趟行程本来就不扣费（{@code TOTAL_AMOUNT=0}）。长周期票（三日 / 七日 / 月票）会出现
 * 该时间**远早于**行程时间的情况，这是**有意为之**：用户 2026-09-15 确认「这趟行程的钱什么时候付的」
 * 答案就是购票时刻，返空串等于什么都不告诉 APP。<b>NEVER 因为「时间看着不对」就改成行程时间或清空。</b></p>
 *
 * <p><b>查不到时三个字段一律留 null，NEVER 编造默认值</b>：调用方据此保持原有的空串输出，
 * 与改造前行为一致。</p>
 */
public class QueryDailyTicketPayInfoResult extends DailyTicketBaseResult {

    /** 支付交易订单号，来源 {@code DAILY_TICKET_ORDER.TRADE_NO} */
    private String payTradeOrderNo;

    /**
     * 购票付款时间，{@code yyyyMMddHHmmss}。
     *
     * <p>来源 {@code DAILY_TICKET_ORDER.PAY_DATE}（{@code TIMESTAMP} 列），
     * 由 daily-ticket-server 侧格式化后返回 —— 与 {@code entryDate} / {@code exitDate} 同格式，
     * <b>NEVER 返回带分隔符的形式</b>，APP 按 14 位定长解析。</p>
     */
    private String payOrderNoDate;

    /** 支付通道编码（{@code 03}=支付宝等），来源 {@code DAILY_TICKET_ORDER.PAY_CHANNEL_CODE} */
    private String payChannelCode;

    public String getPayTradeOrderNo() {
        return payTradeOrderNo;
    }

    public void setPayTradeOrderNo(String payTradeOrderNo) {
        this.payTradeOrderNo = payTradeOrderNo;
    }

    public String getPayOrderNoDate() {
        return payOrderNoDate;
    }

    public void setPayOrderNoDate(String payOrderNoDate) {
        this.payOrderNoDate = payOrderNoDate;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }
}
