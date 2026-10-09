package com.chinasofti.huateng.dailyticket.service.support;

/**
 * {@code DAILY_TICKET_INSTANCE.TICKET_STATUS} 的取值与判定。
 *
 * <p>本项目用 String 字面量而非枚举表达状态（AGENTS.md §2.2.1），因此这些值散落在多处比较点。
 * 收口到这里的目的只有一个：**改动取值时有唯一来源**，
 * 而不是像以前那样要在 god class 里逐个 grep 字面量。
 *
 * <p>状态流转（退款相关那一段）：
 * {@code ACTIVATED} --锁票--> {@code REFUND_LOCKED} --放款--> {@code REFUNDED}，
 * 退款明确失败时 {@code REFUND_LOCKED} --解锁--> {@code ACTIVATED}。
 * 推进动作全部在 {@code DailyTicketTicketLockWriter}，**NEVER 在别处直接 CAS 这三个状态**。
 *
 * <p>{@code REFUND_LOCKED} 与 {@code REFUNDED} 都不在进站白名单（{@code selectForEntryCheck}）里，
 * 这是「已申请退款的票不能再乘坐」的唯一落点，NEVER 把它们加进那个白名单。
 */
public final class DailyTicketInstanceStatus {
    /** 已激活，可进站。 */
    public static final String ACTIVATED = "ACTIVATED";
    /** 已用完（计次票次数耗尽或日票过期前最后一次出站）。 */
    public static final String USED = "USED";
    /** 已过期。 */
    public static final String EXPIRED = "EXPIRED";
    /** 核验退款观察期内被锁定：查不到、也不允许过闸。 */
    public static final String REFUND_LOCKED = "REFUND_LOCKED";
    /** 退款已放款的终态。 */
    public static final String REFUNDED = "REFUNDED";

    private DailyTicketInstanceStatus() {
    }

    /** 票是否处于退款占用态：{@code REFUND_LOCKED} 观察期内或 {@code REFUNDED} 已放款。 */
    public static boolean isLockedForRefund(String ticketStatus) {
        return REFUND_LOCKED.equals(ticketStatus) || REFUNDED.equals(ticketStatus);
    }
}
