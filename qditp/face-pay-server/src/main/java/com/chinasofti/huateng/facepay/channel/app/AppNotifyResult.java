package com.chinasofti.huateng.facepay.channel.app;

/**
 * 一次 APP 通知投递的结果。
 *
 * <p>只有两种状态，<b>没有「不确定」</b>：通知是幂等可重投的（APP 侧按 orderNo 去重），
 * 因此拿不准时按失败重试比按成功丢弃安全。这与支付链路相反——
 * 支付的「对端未答」绝不能当失败，因为钱可能已经扣了。</p>
 *
 * @param delivered     是否已被对端受理
 * @param failureReason 失败原因，成功时为 null
 */
public record AppNotifyResult(boolean delivered, String failureReason) {

    private static final AppNotifyResult DELIVERED = new AppNotifyResult(true, null);

    /**
     * 已投递。
     *
     * <p>工厂方法叫 {@code ok} 而不是 {@code delivered}——record 会为组件
     * {@code delivered} 自动生成同名访问器，静态方法重名会被编译器判为
     * 「记录中的存取方法无效」。</p>
     */
    public static AppNotifyResult ok() {
        return DELIVERED;
    }

    public static AppNotifyResult failed(String reason) {
        return new AppNotifyResult(false, reason);
    }
}
