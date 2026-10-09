package com.chinasofti.huateng.dailyticket.client;

/** ACC 发售通知投递结果。 */
public record DailyTicketAccNotifyResult(boolean delivered, String failureReason) {

    public static DailyTicketAccNotifyResult ok() {
        return new DailyTicketAccNotifyResult(true, null);
    }

    public static DailyTicketAccNotifyResult failed(String reason) {
        return new DailyTicketAccNotifyResult(false, reason);
    }
}
