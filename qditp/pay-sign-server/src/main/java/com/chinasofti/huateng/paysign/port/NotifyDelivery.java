package com.chinasofti.huateng.paysign.port;

/**
 * 一次 APP 出向通知的投递结果。
 *
 * <p>只回答「这一次推成没成、失败原因是什么」，**NEVER 承载重试语义** ——
 * 重试预算与状态回写是 {@code SignNotifyServiceImpl} / {@code TerminationNotifyServiceImpl} 的职责。
 */
public record NotifyDelivery(boolean delivered, String message) {

    /** NEVER 把工厂方法命名成 {@code delivered()} —— 会与记录组件的存取方法同名同参、编译不过。 */
    public static NotifyDelivery succeeded() {
        return new NotifyDelivery(true, "通知成功");
    }

    public static NotifyDelivery failed(String message) {
        return new NotifyDelivery(false, message);
    }
}
