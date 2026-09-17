package com.chinasofti.huateng.facepay.domain;

/** {@code F2F_ORDER.REFUND_STATUS} 的值域。 */
public enum F2fOrderRefundStatus {

    /** 未发生过成功退款。 */
    NONE,
    /** 已成功退回一部分，{@code REFUND_AMOUNT < ORDER_AMOUNT}。 */
    PARTIAL,
    /** 已全额退回，{@code REFUND_AMOUNT >= ORDER_AMOUNT}。 */
    SUCCESS;

    /** 库里的脏值 / 未识别取值一律返 {@code null}，NEVER 抛异常。 */
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
