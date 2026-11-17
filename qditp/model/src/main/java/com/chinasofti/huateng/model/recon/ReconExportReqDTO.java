package com.chinasofti.huateng.model.recon;

import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Data
public class ReconExportReqDTO {

    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final DateTimeFormatter DASHED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String batchId;

    private String businessDate;

    private String windowStart;

    private String windowEnd;

    private List<String> fileTypes;

    public String getStartDate() {
        return windowStart == null ? null : windowStart.substring(0, 8);
    }

    public String getEndDate() {
        return windowEnd == null ? null : windowEnd.substring(0, 8);
    }

    public String getWindowStartDashed() {
        return dashed(windowStart);
    }

    public String getWindowEndDashed() {
        return dashed(windowEnd);
    }

    public Timestamp getWindowStartTimestamp() {
        return timestamp(windowStart);
    }

    public Timestamp getWindowEndTimestamp() {
        return timestamp(windowEnd);
    }


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
