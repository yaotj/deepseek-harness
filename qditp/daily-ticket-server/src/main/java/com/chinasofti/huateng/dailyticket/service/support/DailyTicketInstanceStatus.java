package com.chinasofti.huateng.dailyticket.service.support;


public final class DailyTicketInstanceStatus {
    public static final String ACTIVATED = "ACTIVATED";
    public static final String USED = "USED";
    public static final String EXPIRED = "EXPIRED";
    public static final String REFUND_LOCKED = "REFUND_LOCKED";
    public static final String REFUNDED = "REFUNDED";

    private DailyTicketInstanceStatus() {
    }

    public static boolean isLockedForRefund(String ticketStatus) {
        return REFUND_LOCKED.equals(ticketStatus) || REFUNDED.equals(ticketStatus);
    }
}
