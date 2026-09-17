package com.chinasofti.huateng.facepay.support;

/** 受理渠道码，对应 {@code F2F_ORDER.CHANNEL} 与 {@code F2F_DEVICE_STATUS.CHANNEL}， 受 {@code CK_F2F_ORDER_CHANNEL CHECK (CHANNEL IN ('01','02','03'))} 约束。 */
public final class F2fChannel {

    /** APP。 */
    public static final String APP = "01";

    /** TVM 自动售票机。 */
    public static final String TVM = "02";

    /** BOM 车站客服中心终端。 */
    public static final String BOM = "03";

    /**
     * 设备报文里 {@code providerId} 到渠道码的映射。
     *
     * @return 渠道码；{@code providerId} 为空时返回 null，调用方按非法参数拒绝
     */
    public static String fromProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        return switch (providerId) {
            case APP, TVM, BOM -> providerId;
            default -> null;
        };
    }

    private F2fChannel() {
    }
}
