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

    /** 允许发起退款的前置状态白名单。 */
    public static boolean isRefundable(String value) {
        return SUCCESS.is(value) || PROCESSING.is(value);
    }
}
