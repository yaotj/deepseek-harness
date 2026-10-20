package com.chinasofti.huateng.facepay.support;

/**
 * 设备号解析口径（唯一真源）。
 *
 * <p>设备报文（TVM / BOM）**都不带站码**，设备号本身是车站与站内序号的拼接，故「由设备号推站码」这条规则
 * 在模块里有多处需求：心跳落库要按站统计、BOM 票卡分析要判同站/跨站、运营端设备页要按站展示。
 * 解析 MUST 只在这里做一份，NEVER 在调用方各写一遍 {@code substring(0, 4)}。
 *
 * <p><b>规则来源</b>：ADR-D157（票卡分析站点感知）——{@code deviceId} 前 4 位即所属站码，与 TVM 同口径，
 * 已用 {@code STATION_INFO} 核验：{@code 0622}=辛屯、{@code 0352}=双山、{@code 1128}=世博园、
 * {@code 0220}=国际邮轮港。
 *
 * <p><b>刻意不做 trim / 大小写归一</b>：设备号自身就是 {@code F2F_DEVICE_STATUS} 复合主键的一部分，
 * 站码 MUST 与同一个原值同源；若在此悄悄规整，就会出现「行的键是补过空格的、站码却是规整后的」这种自相矛盾。
 * 补空格的设备号属上游报文错误，会产生独立的一行（这是既有行为，不在本类修正范围）。
 *
 * <p><b>已知的同规则第二处（刻意不合并）</b>：{@code TvmResponses.topupOrderDetail} 用的是
 * {@code deviceId.length() <= 4} 而非 {@code < 4}。其 javadoc 自陈「照搬旧判据」，是对外应答契约的兼容，
 * 改成 {@code < 4} 会变更 TVM 充值单详情里的 {@code singlePickupStationCode}，故保留原样。
 * 本类只管「设备号 → 站码」这条规则，不替对方决定契约兼容。
 */
public final class F2fDeviceId {

    /** 站码长度，即设备号前缀位数。 */
    public static final int STATION_CODE_LENGTH = 4;

    /**
     * 由设备号推导所属站码。
     *
     * @param deviceId 设备号原值，形如 {@code 06220801} / {@code 02441101}；可空
     * @return 前 4 位站码；设备号为空或不足 4 位时返回 {@code null}（推不出来就不猜，猜错会把运维引到错误的站）
     */
    public static String stationCode(String deviceId) {
        if (deviceId == null || deviceId.length() < STATION_CODE_LENGTH) {
            return null;
        }
        return deviceId.substring(0, STATION_CODE_LENGTH);
    }

    private F2fDeviceId() {
    }
}
