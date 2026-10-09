package com.chinasofti.huateng.gatetxnpay.constant;

/** `GATE_TXN_PAY.DEBIT_STATUS` 的唯一取值来源。 */
public enum DebitStatus {
    /** 已落单、尚未发起扣款（含离线码待重算态）。 */
    INIT("INIT"),
    /** 已向支付域发起扣款、等回调。 */
    PROCESSING("PROCESSING"),
    /** 发起失败或无响应，留给补偿重试。 */
    RETRY("RETRY"),
    /** 终态：已收到钱（免扣费与日票交易落单即此态）。 */
    SUCCESS("SUCCESS"),
    /** 终态：扣款失败。 */
    FAIL("FAIL");

    private final String code;

    DebitStatus(String code) {
        this.code = code;
    }

    /** 落库值。 */
    public String code() {
        return code;
    }

    public boolean is(String value) {
        return code.equals(value);
    }

    /** 允许运营重试免密扣款的前置状态白名单。 */
    public static boolean isRetryable(String value) {
        return RETRY.is(value) || INIT.is(value);
    }

    /**
     * 允许「每日批量重试」的前置状态白名单，**比 {@link #isRetryable} 多一个终态 {@code FAIL}**。
     *
     * <p>两者刻意分开：运营在页面上点重试是人工判断过的单笔操作，只该碰未收口的 {@code INIT / RETRY}；
     * 批量重试是甲方需求「行程扣费重试」，业主已裁决**包含 FAIL**（支付中心业务拒绝后签约可能已修复）。
     * **NEVER 把 FAIL 并进 {@link #isRetryable}** —— 那会让运营页面把已终态失败的单也重发，绕过人工确认。
     * 防止无限重扣靠 {@code DEBIT_RETRY_TIMES} 上限与 {@code DEBIT_NEXT_RETRY_TIME} 退避，不靠状态收窄。
     */
    public static boolean isBatchRetryable(String value) {
        return RETRY.is(value) || FAIL.is(value);
    }

    /** 允许发起退款的前置状态白名单。 */
    public static boolean isRefundable(String value) {
        return SUCCESS.is(value) || PROCESSING.is(value);
    }
}
