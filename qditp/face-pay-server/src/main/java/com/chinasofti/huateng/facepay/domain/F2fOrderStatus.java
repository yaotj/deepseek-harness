package com.chinasofti.huateng.facepay.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code F2F_ORDER.ORDER_STATUS} 的状态机。 */
public enum F2fOrderStatus {

    /** 已下单，未发起支付。 */
    CREATED,
    /** 已向支付中心发起支付，结果未定。 */
    PAYING,
    /** 支付成功。 */
    PAID,
    /** 业务已履约（出票 / 充值成功）。 */
    FULFILLED,
    /** 业务履约失败（出票失败 / 充值失败），后续通常进 {@code REFUNDING}。 */
    FULFILL_FAILED,
    /** 充值可疑，需人工核实。 */
    TOPUP_SUSPECT,
    /** 支付失败（支付中心明确拒绝或返回失败）。 */
    PAY_FAILED,
    /** 二维码超时未支付 / 支付中心判定未支付。 */
    EXPIRED,
    /** 退款已提交，等待支付中心收口。 */
    REFUNDING,
    /** 退款成功。 */
    REFUNDED,
    /** 订单取消。 */
    CANCELED;

    /** 迁移白名单，逐条来自 2026-09-14 对 9 个服务全部 40 余个写入点的实测，**不是设计稿**。 */
    private static final Map<F2fOrderStatus, Set<F2fOrderStatus>> ALLOWED = Map.of(
            CREATED, EnumSet.of(PAYING, PAID, PAY_FAILED, EXPIRED),
            PAYING, EnumSet.of(PAID, PAY_FAILED, EXPIRED),
            PAID, EnumSet.of(FULFILLED, FULFILL_FAILED, REFUNDING),
            FULFILLED, EnumSet.of(REFUNDING),
            FULFILL_FAILED, EnumSet.of(REFUNDING),
            REFUNDING, EnumSet.of(REFUNDED),
            PAY_FAILED, EnumSet.noneOf(F2fOrderStatus.class),
            EXPIRED, EnumSet.noneOf(F2fOrderStatus.class),
            REFUNDED, EnumSet.noneOf(F2fOrderStatus.class),
            CANCELED, EnumSet.noneOf(F2fOrderStatus.class));

    /** 「未支付、可继续支付」。 */
    public static final List<String> PENDING = names(CREATED, PAYING);

    /** 「已收到钱、可退」。 */
    public static final List<String> REFUNDABLE = names(PAID, FULFILLED, FULFILL_FAILED);

    /** 「失败态」读取集合，四个服务各有一份，其中三份带裸字面量 {@code "EXPIRED"} / {@code "CANCELED"}。 */
    public static final List<String> FAILED_LIKE = names(PAY_FAILED, EXPIRED, CANCELED);

    /** 「已支付待履约」，出票 / 充值成功失败四个写入点的前置状态。 */
    public static final List<String> FULFILLABLE = names(PAID);

    /** 宽松解析：库里的脏值 / 新加但代码未识别的取值一律返 {@code null}，NEVER 抛异常。 */
    public static F2fOrderStatus parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        for (F2fOrderStatus st : values()) {
            if (st.name().equals(trimmed)) {
                return st;
            }
        }
        return null;
    }

    /** 只做快速失败与错误提示，NEVER 当作并发保证。 */
    public boolean canTransitTo(F2fOrderStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 终态即无出边。 */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    private static List<String> names(F2fOrderStatus... statuses) {
        return List.of(java.util.Arrays.stream(statuses).map(Enum::name).toArray(String[]::new));
    }
}
