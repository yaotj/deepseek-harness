package com.chinasofti.huateng.dailyticket.client;

/**
 * 一次 APP 通知投递的结果。
 *
 * @param delivered     是否已被对端受理
 * @param failureReason 失败原因，成功时为 null
 */
public record DailyTicketAppNotifyResult(boolean delivered, String failureReason) {

    private static final DailyTicketAppNotifyResult DELIVERED = new DailyTicketAppNotifyResult(true, null);

    /** 已投递。 */
    public static DailyTicketAppNotifyResult ok() {
        return DELIVERED;
    }

    public static DailyTicketAppNotifyResult failed(String reason) {
        return new DailyTicketAppNotifyResult(false, reason);
    }
}
