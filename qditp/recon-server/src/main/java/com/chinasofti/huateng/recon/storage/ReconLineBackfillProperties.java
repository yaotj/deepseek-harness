package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * PAY 汇总文件「线路段」补齐的配置：由本模块在聚合完成、写文件之前按车站码统一补齐并无条件覆盖。
 */
@ConfigurationProperties(prefix = "recon.line-backfill")
public class ReconLineBackfillProperties {

    /** 总开关。关掉即完全不查 {@code STATION_INFO}、原样写出源上送的键段。 */
    private boolean enabled = true;

    /** 需要补线路的文件类型名（{@code ReconFileType} 的枚举名），只有 PAY 有线路 / 车站两段。 */
    private Set<String> fileTypes = new LinkedHashSet<>(Set.of("PAY"));

    /** 线路段在行中的下标（0 基）。甲方 21 段 PAY 行格式的第 2 段。 */
    private int lineFieldIndex = 1;

    /** 车站段在行中的下标（0 基）。甲方 21 段 PAY 行格式的第 3 段，补线路时按它查维表。 */
    private int stationFieldIndex = 2;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<String> getFileTypes() { return fileTypes; }
    public void setFileTypes(Set<String> fileTypes) {
        this.fileTypes = fileTypes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(fileTypes);
    }
    public int getLineFieldIndex() { return lineFieldIndex; }
    public void setLineFieldIndex(int lineFieldIndex) { this.lineFieldIndex = lineFieldIndex; }
    public int getStationFieldIndex() { return stationFieldIndex; }
    public void setStationFieldIndex(int stationFieldIndex) { this.stationFieldIndex = stationFieldIndex; }

    /** 该文件类型是否需要补线路。类型名大小写不敏感，避免配置里写成小写就静默失效。 */
    public boolean appliesTo(String fileTypeName) {
        if (!enabled || fileTypeName == null) return false;
        return fileTypes.stream().anyMatch(fileTypeName::equalsIgnoreCase);
    }
}
