package com.chinasofti.huateng.dailyticket.client;

public record DailyTicketAccNotifyResult(boolean delivered, String failureReason) {

    public static DailyTicketAccNotifyResult ok() {
        return new DailyTicketAccNotifyResult(true, null);
    }

    public static DailyTicketAccNotifyResult failed(String reason) {
        return new DailyTicketAccNotifyResult(false, reason);
    }
}
