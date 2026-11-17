package com.chinasofti.huateng.dailyticket.client;


public record DailyTicketAppNotifyResult(boolean delivered, String failureReason) {

    private static final DailyTicketAppNotifyResult DELIVERED = new DailyTicketAppNotifyResult(true, null);

    public static DailyTicketAppNotifyResult ok() {
        return DELIVERED;
    }

    public static DailyTicketAppNotifyResult failed(String reason) {
        return new DailyTicketAppNotifyResult(false, reason);
    }
}
