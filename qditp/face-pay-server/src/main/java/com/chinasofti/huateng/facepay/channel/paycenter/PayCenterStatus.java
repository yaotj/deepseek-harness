package com.chinasofti.huateng.facepay.channel.paycenter;

/**
 * 支付中心 {@code data.status} 取值。
 *
 * <p><b>ORDERED 与「查不到」不是一回事，也都不等于失败。</b>旧实现在 {@code else} 分支
 * 「按已下单-支付中处理」，本枚举保留 {@link #ORDERED}；解析不出取值时
 * {@link #fromCode(String)} 返回 {@code null}，调用方 MUST 落
 * {@code F2F_PAYMENT.PAY_STATUS='UNKNOWN'} 并靠查询接口收口，NEVER 直接判 FAILED。</p>
 *
 * <p><b>{@link #FAIL} 是支付中心真实下发的失败取值，{@link #FAILED} 从未被观测到。</b>
 * 旧 {@code PayCenterStatusEnum} 只有 {@code FAILED}，于是支付失败回调永远落进
 * 「状态不明确」分支回 {@code -1}，支付中心按退避无限重推——旧实现的既有缺陷，
 * 2026-09-11 并跑期间在新服务上复现为重推风暴。同一份网关文档的退款回调
 * （{@code refundResult}）明确写的是 {@code FAIL}（旧
 * {@code PayCenterRefundStatusEnum.REFUNDING_FAIL} 也是 {@code FAIL}），
 * 支付回调 §5.1 则没有列出 {@code status} 值域。因此这里两个都收，
 * 判失败 MUST 走 {@link #isFailed()}，NEVER 写 {@code == FAILED}。</p>
 */
public enum PayCenterStatus {

    ORDERED("ORDERED"),
    SUCCESS("SUCCESS"),
    /** 支付中心实际下发的失败取值。 */
    FAIL("FAIL"),
    /** 文档未列、实测未见的失败取值，保留兜底，NEVER 单独判它。 */
    FAILED("FAILED"),
    UNPAID("UNPAID");

    private final String code;

    PayCenterStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 支付失败。{@code FAIL} 与 {@code FAILED} 等价，调用方 MUST 用本方法而非 {@code ==}。 */
    public boolean isFailed() {
        return this == FAIL || this == FAILED;
    }

    /** 未知取值返回 {@code null}，由调用方按 UNKNOWN 处理。 */
    public static PayCenterStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayCenterStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
