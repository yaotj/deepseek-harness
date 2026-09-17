package com.chinasofti.huateng.model.recon;

import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * recon-server 下发给源服务的抽取指令（IF-RECON-01）。
 */
@Data
public class ReconExportReqDTO {

    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final DateTimeFormatter DASHED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 批次标识，形如 {@code RECON20260910}。 */
    private String batchId;

    /** 账期日期 {@code yyyyMMdd}，即最终文件名后缀。 */
    private String businessDate;

    /** 窗口起点（含），{@code yyyyMMddHHmmss}。 */
    private String windowStart;

    /** 窗口终点（不含），{@code yyyyMMddHHmmss}。 */
    private String windowEnd;

    /** 本源需要产出的文件类型名清单，如 {@code ["DETAIL","PAY"]}。 */
    private List<String> fileTypes;

    /** 窗口起点的 {@code yyyyMMdd} 部分，用于月分区表的分区裁剪。 */
    public String getStartDate() {
        return windowStart == null ? null : windowStart.substring(0, 8);
    }

    /** 窗口终点的 {@code yyyyMMdd} 部分，用于月分区表的分区裁剪。 */
    public String getEndDate() {
        return windowEnd == null ? null : windowEnd.substring(0, 8);
    }

    /** 窗口起点转 {@code yyyy-MM-dd HH:mm:ss}，供 collect-pay-server 的字符串时间列使用。 */
    public String getWindowStartDashed() {
        return dashed(windowStart);
    }

    /** 窗口终点转 {@code yyyy-MM-dd HH:mm:ss}，供 collect-pay-server 的字符串时间列使用。 */
    public String getWindowEndDashed() {
        return dashed(windowEnd);
    }

    /** 窗口起点转 {@link Timestamp}，供 daily-ticket-server 的 TIMESTAMP 列使用。 */
    public Timestamp getWindowStartTimestamp() {
        return timestamp(windowStart);
    }

    /** 窗口终点转 {@link Timestamp}，供 daily-ticket-server 的 TIMESTAMP 列使用。 */
    public Timestamp getWindowEndTimestamp() {
        return timestamp(windowEnd);
    }

    /**
     * 校验必填项与窗口方向，缺失或倒挂直接抛异常。
     */
    public void validate() {
        require(batchId, "batchId");
        require(businessDate, "businessDate");
        require(windowStart, "windowStart");
        require(windowEnd, "windowEnd");
        if (businessDate.length() != 8 || !businessDate.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("businessDate 必须是 8 位 yyyyMMdd: " + businessDate);
        }
        if (windowStart.length() != 14 || windowEnd.length() != 14) {
            throw new IllegalArgumentException("时间窗口必须是 14 位 yyyyMMddHHmmss");
        }
        if (windowStart.compareTo(windowEnd) >= 0) {
            throw new IllegalArgumentException("时间窗口倒挂: " + windowStart + " -> " + windowEnd);
        }
        if (fileTypes == null || fileTypes.isEmpty()) {
            throw new IllegalArgumentException("fileTypes 为空");
        }
        fileTypes.forEach(ReconFileTypeEnum::of);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("对账抽取指令缺少 " + name);
        }
    }

    private static String dashed(String compact) {
        return compact == null ? null : LocalDateTime.parse(compact, COMPACT).format(DASHED);
    }

    private static Timestamp timestamp(String compact) {
        return compact == null ? null : Timestamp.valueOf(LocalDateTime.parse(compact, COMPACT));
    }
}
