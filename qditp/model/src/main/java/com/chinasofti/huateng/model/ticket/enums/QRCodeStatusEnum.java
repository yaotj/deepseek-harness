package com.chinasofti.huateng.model.ticket.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * QR码车票状态枚举。
 * <p>对应 QRCODE_STATUS 表的 codeStatus 字段</p>
 *
 * <p>迁移白名单见 {@link #ALLOWED}。<b>白名单只做解析与文档化，NEVER 当作并发保证</b>——
 * 并发保证是 {@code QRCodeStatusMapper.upsertWithCas} 的 {@code TXN_SEQ} CAS 条件。
 * 与 {@code model/domain/SignStatus} 的定位一致（见 docs/domain/state-machines.md §二③ 约束 2）。</p>
 */
public enum QRCodeStatusEnum {

    /** 01 - 无交易 */
    NO_TXN("01", "无交易"),

    /** 02 - 结束行程 */
    END_TRIP("02", "结束行程"),

    /** 03 - 初始化 */
    SJT_ISSUE("03", "初始化"),

    /** 04 - 已进站 */
    ENTRY("04", "已进站"),

    /** 05 - 已出站 */
    EXIT("05", "已出站"),

    /** 06 - 超时出站 */
    EXIT_OVERTIME("06", "超时出站"),

    /** 08 - 20分钟内免费更新（BOM/PCA非付费区） */
    UPDATE_FREE("08", "20分钟内免费更新"),

    /** 09 - 20分钟内付费更新（BOM/PCA非付费区） */
    UPDATE_PAY("09", "20分钟内付费更新"),

    /** 10 - 入站码更新 */
    UPDATE_ENTRY("10", "入站码更新"),

    /** 70 - 异常 */
    ABNORMAL("70", "异常"),

    /** 80 - APP自助补出站更新 */
    SELF_SERVICE_EXIT("80", "APP自助补出站更新"),

    /** 81 - APP自助补进站更新 */
    SELF_SERVICE_ENTRY("81", "APP自助补进站更新"),

    /** FF - 进站失败 */
    ENTRY_FAIL("FF", "进站失败");

    private final String code;
    private final String desc;

    QRCodeStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据编码获取QR码状态枚举。
     */
    public static QRCodeStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QRCodeStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为闭环状态（02/05/06/80）。
     */
    public boolean isClosedLoop() {
        return this == END_TRIP || this == EXIT || this == EXIT_OVERTIME || this == SELF_SERVICE_EXIT;
    }

    /**
     * 判断是否为开环状态（04/81）。
     */
    public boolean isOpenLoop() {
        return this == ENTRY || this == SELF_SERVICE_ENTRY;
    }

    /**
     * 迁移白名单。按 2026-09-14 全量梳理写入方得出，**只做解析与文档化**：
     * <ul>
     *   <li>过闸链路 {@code GateTicketHandler.buildNextStatus}：04 / 05 / 06 / 08 / 09 / FF / 70 / 01</li>
     *   <li>开卡复位 {@code TicketRideStatusServiceImpl.registerRideStatus} 与
     *       {@code ExcessFareHandler} 建新行：03</li>
     *   <li>APP 自助补站落 80 / 81，BOM 单边处理经 {@code adviceOpt} 落 04 / 08 / 09</li>
     * </ul>
     * <p><b>80 / 81 自 2026-09-14 起真正可达</b>：`GateTicketHandler.resolveExcessFareTypeCodeStatus`
     * 按 {@code excessFareType}（01 → 81 补进站 / 02 → 80 补出站）解析，优先级在 {@code trxType} 之前。
     * 此前该形参只进日志、两个状态无写入路径，本白名单的 80 / 81 入边也因此不全 —— 现已按
     * {@code ExcessFareHandler.resolveAllowedTypes} 的前置状态集合补齐，**改那个方法的允许集合时
     * MUST 同步看齐这里**，否则每笔补站都会打一条「迁移不在白名单内」WARN。
     * {@code excessFareType=03 / 04} 按用户裁决暂不映射，仍走 trxType 落 06 / FF。</p>
     * <p><b>10（入站码更新）没有任何写入路径</b>（2026-09-09 实测生产库 0 行），
     * 它的出边是理论值、当前不可达，NEVER 把新功能的前置条件挂在 10 上。</p>
     * <p>运营端 {@code updateCodeStatus} 是人工改值，**刻意不受本白名单约束**。</p>
     */
    private static final Map<QRCodeStatusEnum, Set<QRCodeStatusEnum>> ALLOWED = Map.ofEntries(
            Map.entry(NO_TXN, EnumSet.of(ENTRY)),
            Map.entry(SJT_ISSUE, EnumSet.of(ENTRY, ENTRY_FAIL, SELF_SERVICE_ENTRY)),
            Map.entry(ENTRY, EnumSet.of(EXIT, EXIT_OVERTIME, UPDATE_FREE, UPDATE_PAY, SELF_SERVICE_EXIT)),
            Map.entry(EXIT, EnumSet.of(ENTRY, ENTRY_FAIL, SELF_SERVICE_ENTRY)),
            Map.entry(EXIT_OVERTIME, EnumSet.of(ENTRY, ENTRY_FAIL, SELF_SERVICE_ENTRY)),
            Map.entry(END_TRIP, EnumSet.of(ENTRY, SELF_SERVICE_ENTRY)),
            Map.entry(UPDATE_FREE, EnumSet.of(ENTRY, EXIT, EXIT_OVERTIME, SELF_SERVICE_ENTRY)),
            Map.entry(UPDATE_PAY, EnumSet.of(ENTRY, EXIT, EXIT_OVERTIME, SELF_SERVICE_ENTRY)),
            Map.entry(UPDATE_ENTRY, EnumSet.of(EXIT, EXIT_OVERTIME, UPDATE_FREE, SELF_SERVICE_ENTRY)),
            Map.entry(SELF_SERVICE_EXIT, EnumSet.of(ENTRY, ENTRY_FAIL, SELF_SERVICE_ENTRY)),
            Map.entry(SELF_SERVICE_ENTRY, EnumSet.of(EXIT, EXIT_OVERTIME, UPDATE_PAY, SELF_SERVICE_EXIT, ENTRY_FAIL)),
            Map.entry(ENTRY_FAIL, EnumSet.of(ENTRY)),
            Map.entry(ABNORMAL, EnumSet.noneOf(QRCodeStatusEnum.class)));

    /**
     * 无论当前状态为何都允许落入的目标态。
     * <p>70 由 {@code trxType=99}（闸机异常）产生、03 由开卡复位产生，两者都不看前置状态；
     * FF（进站失败）只在 03 / 05 / 06 / 80 / FF 之后合法，因此 <b>NEVER 加进本集合</b>。</p>
     */
    private static final Set<QRCodeStatusEnum> ALWAYS_ALLOWED_TARGETS = EnumSet.of(ABNORMAL, SJT_ISSUE);

    /**
     * 只做快速判定与告警提示，NEVER 当作并发保证（并发保证是 {@code upsertWithCas} 的 CAS 条件），
     * 也 NEVER 用它在 CAS 之前拦请求——调用方手里的「当前状态」来自更早一次 select、随时可能过期。
     */
    public boolean canTransitTo(QRCodeStatusEnum target) {
        if (target == null) {
            return false;
        }
        if (this == target || ALWAYS_ALLOWED_TARGETS.contains(target)) {
            return true;
        }
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * 宽松解析：无法识别时返回 null 而不抛异常。
     * <p>上游可能上送未登记的取值，解析失败 MUST 由调用方决定处置，NEVER 在这里抛。</p>
     */
    public static QRCodeStatusEnum parseOrNull(String code) {
        return fromCode(code);
    }
}
