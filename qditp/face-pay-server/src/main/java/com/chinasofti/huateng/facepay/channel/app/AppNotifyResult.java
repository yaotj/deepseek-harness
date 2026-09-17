package com.chinasofti.huateng.facepay.channel.app;

/**
 * 一次 APP 通知投递的结果。
 *
 * @param delivered     是否已被对端受理
 * @param failureReason 失败原因，成功时为 null
 */
public record AppNotifyResult(boolean delivered, String failureReason) {

    private static final AppNotifyResult DELIVERED = new AppNotifyResult(true, null);

    /** 已投递。 */
    public static AppNotifyResult ok() {
        return DELIVERED;
    }

    public static AppNotifyResult failed(String reason) {
        return new AppNotifyResult(false, reason);
    }
}
