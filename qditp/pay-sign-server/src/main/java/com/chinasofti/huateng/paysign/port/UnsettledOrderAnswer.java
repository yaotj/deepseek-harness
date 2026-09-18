package com.chinasofti.huateng.paysign.port;

/**
 * 「有没有未结清扣费订单」的三态答复（2026-09-17，ADR-D119）。
 *
 * <p><b>为什么必须是三态、NEVER 用 boolean</b>：这个答复是解约的前置闸门，
 * 而 {@code false}（确认无欠费）与「问不出来」在业务上是相反的处置 ——
 * 前者放行解约，后者 MUST 拦住。折叠成 boolean 之后，
 * 「闸机域超时」「闸机域返 9999」都会变成「该用户没欠费」，于是**用户欠着钱把签约解掉、这笔钱再也扣不到**，
 * 且全程不报错：解约接口返 {@code 0000}、调度日志一片绿。
 *
 * <p>收口前两个调用点的判据本来就不一致：{@code TerminationProcessor} 判了 {@code resultCode}，
 * {@code TerminationInternalServiceImpl} 只判了 {@code null} —— 后者在「闸机域答了但不是成功码」时
 * 会把 {@code hasFailedOrder} 的默认值 {@code false} 当成答案透传出去。本类型的存在就是让这种漏判编译不过。
 */
public sealed interface UnsettledOrderAnswer {

    /** 闸机域按成功码答复，{@code hasUnsettledOrder} 可信。 */
    record Answered(boolean hasUnsettledOrder) implements UnsettledOrderAnswer {
    }

    /** 闸机域答了但不是成功码（含响应体为空）：**问不出来**，NEVER 当成「无欠费」。 */
    record Rejected(String retCode, String retMsg) implements UnsettledOrderAnswer {
    }

    /** 没拿到业务答复（超时 / 连不上）：同样是**问不出来**。 */
    record Unknown(Throwable cause) implements UnsettledOrderAnswer {
    }
}
