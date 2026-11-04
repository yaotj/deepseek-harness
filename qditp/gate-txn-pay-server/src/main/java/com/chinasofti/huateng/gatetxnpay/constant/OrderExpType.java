package com.chinasofti.huateng.gatetxnpay.constant;

/**
 * {@code GATE_TXN_PAY.ORDER_EXP_TYPE} 的唯一取值来源。
 *
 * <p>共六档：<code>0=正常</code> / <code>1=单边入</code> / <code>2=单边出</code> /
 * <code>3=单边入站人工</code> / <code>4=单边出站人工</code> / <code>5=双段计费超时</code>。
 *
 * <p>甲方口径 {@code 1~15} 与我方 {@code 0~5} 的映射待甲方澄清，**NEVER 自行折算**。
 *
 * @see <a href="https://github.com/chinasofti/qditp/blob/main/docs/domain/decisions.md">ADR-D162</a>
 */
public enum OrderExpType {

    /** 正常交易，有进有出、结算完整。 */
    NORMAL("0"),
    /** 单边账(入)：进站记录存在、出站缺失。需等乘客出站后补扣，或自动创建补款单。 */
    ONE_SIDE_IN("1"),
    /** 单边账(出)：出站记录存在、进站缺失。视为无效行程，需退款。 */
    ONE_SIDE_OUT("2"),
    /** 单边入站人工：BOM 现场补进站。 */
    MANUAL_IN("3"),
    /** 单边出站人工：BOM 现场补出站。 */
    MANUAL_OUT("4"),
    /** 双段计费超时：行程跨两个计费区段、按超时处理。 */
    DUAL_SEGMENT_TIMEOUT("5");

    private final String code;

    OrderExpType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean is(String value) {
        return code.equals(value);
    }

    /** 是否为单边入（需补扣或创建补款单）。 */
    public static boolean isOneSideIn(String value) {
        return ONE_SIDE_IN.is(value);
    }

    /** 是否为单边出（需退款）。 */
    public static boolean isOneSideOut(String value) {
        return ONE_SIDE_OUT.is(value);
    }

    /** 是否为单边账（含入/出）。 */
    public static boolean isOneSide(String value) {
        return ONE_SIDE_IN.is(value) || ONE_SIDE_OUT.is(value);
    }
}
