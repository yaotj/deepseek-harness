package com.chinasofti.huateng.facepay.domain;

/**
 * {@code F2F_ORDER.REFUND_STATUS} 的值域。<b>与 {@link F2fOrderStatus} 正交</b>（ADR-D88）：
 * 退款 NEVER 改 {@code ORDER_STATUS}，只改 {@code REFUND_STATUS} / {@code REFUND_AMOUNT}
 * / {@code LAST_REFUND_TMS} 这三列。参考实现是 {@code PAY_TXN_DETAIL} —— 那张表的
 * {@code PAY_STATUS} 与 {@code REFUND_STATUS} 并列，退款从不覆盖支付主状态。
 *
 * <p><b>为什么必须正交</b>：把退款塞进主状态机后，一笔订单的「钱收了没」「票出了没」
 * 「退了多少」三件事被压成一个字段，于是 {@code FULFILLED} 的订单一退款就丢掉了
 * 「已出票」这个事实，运营端与设备侧再也分不清「退款前出过票」和「从未出票」。
 * 部分退更是根本表达不了。</p>
 *
 * <p><b>本枚举不是权威值域，DDL 的 {@code CK_F2F_ORDER_REFUND_STATUS} 才是</b>
 * （{@code f2f-schema.sql} 与 {@code f2f-order-refund-summary-migration.sql}）。
 * 加取值 MUST 同时改那条约束，只改枚举会在写入时报 ORA-02290。</p>
 *
 * <p><b>没有「退款中」这一档</b>：本枚举描述的是<b>已收口的结果</b>，由
 * {@code F2fOrderMapper.updateRefundSummary} 从 {@code F2F_REFUND} 里
 * {@code REFUND_STATUS='SUCCESS'} 的行重算而来。在途退款的存在性 MUST 查
 * {@code countUnsettledRefunds}，<b>NEVER 在这里加 {@code REFUNDING}</b> ——
 * 那会把「在途」和「已退成」混进同一个字段，退回被本次改造废弃的旧形态。</p>
 */
public enum F2fOrderRefundStatus {

    /** 未发生过成功退款。列默认值。 */
    NONE,
    /** 已成功退回一部分，{@code REFUND_AMOUNT < ORDER_AMOUNT}。 */
    PARTIAL,
    /** 已全额退回，{@code REFUND_AMOUNT >= ORDER_AMOUNT}。 */
    SUCCESS;

    /** 库里的脏值 / 未识别取值一律返 {@code null}，NEVER 抛异常。与 {@code F2fOrderStatus} 同口径。 */
    public static F2fOrderRefundStatus parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        for (F2fOrderRefundStatus st : values()) {
            if (st.name().equals(trimmed)) {
                return st;
            }
        }
        return null;
    }

    /** 已发生过成功退款（部分或全额）。 */
    public static boolean refunded(String raw) {
        F2fOrderRefundStatus st = parseOrNull(raw);
        return st == PARTIAL || st == SUCCESS;
    }
}
