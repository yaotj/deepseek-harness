package com.chinasofti.huateng.facepay.support;

/**
 * 受理渠道码，对应 {@code F2F_ORDER.CHANNEL} 与 {@code F2F_DEVICE_STATUS.CHANNEL}，
 * 受 {@code CK_F2F_ORDER_CHANNEL CHECK (CHANNEL IN ('01','02','03'))} 约束。
 *
 * <p>写成常量而不是枚举，是为了和本项目「多数模块用 String 字面量表达状态」的既有做法一致
 * （AGENTS.md §2.2.1）——枚举在 mapper 参数与 JSON 报文之间来回转换只会增加转换点。</p>
 *
 * <p><b>STT 渠道尚未接入且编码口径未定</b>：{@code DeviceTypeEnum} 记 14、
 * {@code BaseRequestDTO} 注释记 07，两者矛盾。确认后才能加常量并放开 CHECK 约束，
 * 见 {@code f2f-schema.sql} 里 {@code F2F_ORDER.CHANNEL} 的列注释。</p>
 */
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
     * <p>设备侧 {@code providerId} 与本表 {@code CHANNEL} 恰好同码（{@code 03} 都表示 BOM），
     * 旧实现正是直接用 {@code "03".equals(providerId)} 分流。<b>但两者语义不同</b>，
     * 一旦哪天不再同码，改这一个方法即可，不必全局 grep 字面量。</p>
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
