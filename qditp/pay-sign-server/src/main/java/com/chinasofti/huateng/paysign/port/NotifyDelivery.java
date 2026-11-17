package com.chinasofti.huateng.paysign.port;


public record NotifyDelivery(boolean delivered, String message) {

    public static NotifyDelivery succeeded() {
        return new NotifyDelivery(true, "通知成功");
    }

    public static NotifyDelivery failed(String message) {
        return new NotifyDelivery(false, message);
    }
}
