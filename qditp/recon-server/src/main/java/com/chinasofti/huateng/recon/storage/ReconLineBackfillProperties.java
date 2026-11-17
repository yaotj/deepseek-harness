package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;


@ConfigurationProperties(prefix = "recon.line-backfill")
public class ReconLineBackfillProperties {

    private boolean enabled = true;

    private Set<String> fileTypes = new LinkedHashSet<>(Set.of("PAY"));

    private int lineFieldIndex = 1;

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

    public boolean appliesTo(String fileTypeName) {
        if (!enabled || fileTypeName == null) return false;
        return fileTypes.stream().anyMatch(fileTypeName::equalsIgnoreCase);
    }
}
