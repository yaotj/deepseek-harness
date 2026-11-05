package com.chinasofti.huateng.facepay.channel.app;

public record AppNotifyResult(boolean delivered, String failureReason) {

    private static final AppNotifyResult DELIVERED = new AppNotifyResult(true, null);

    public static AppNotifyResult ok() {
        return DELIVERED;
    }

    public static AppNotifyResult failed(String reason) {
        return new AppNotifyResult(false, reason);
    }
}
