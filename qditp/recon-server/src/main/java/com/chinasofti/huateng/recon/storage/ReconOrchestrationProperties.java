package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@ConfigurationProperties(prefix = "recon.orchestration")
public class ReconOrchestrationProperties {

    private boolean enabled = true;

    private long advanceDelayMillis = 60000L;


    private long runTimeoutMillis = 240000L;

    private int windowOffsetDays = 2;


    private Map<String, Integer> windowOffsetDaysByType = new HashMap<>();

    private String windowStartTime = "020000";

    private int maxRetry = 3;

    private List<SourceExpectation> sources = new ArrayList<>();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getAdvanceDelayMillis() { return advanceDelayMillis; }

    public void setAdvanceDelayMillis(long advanceDelayMillis) { this.advanceDelayMillis = advanceDelayMillis; }

    public long getRunTimeoutMillis() { return runTimeoutMillis; }

    public void setRunTimeoutMillis(long runTimeoutMillis) { this.runTimeoutMillis = runTimeoutMillis; }

    public int getWindowOffsetDays() { return windowOffsetDays; }

    public void setWindowOffsetDays(int windowOffsetDays) { this.windowOffsetDays = windowOffsetDays; }

    public Map<String, Integer> getWindowOffsetDaysByType() { return windowOffsetDaysByType; }

    public void setWindowOffsetDaysByType(Map<String, Integer> windowOffsetDaysByType) {
        this.windowOffsetDaysByType = windowOffsetDaysByType == null ? new HashMap<>() : new HashMap<>(windowOffsetDaysByType);
    }

    public String getWindowStartTime() { return windowStartTime; }

    public void setWindowStartTime(String windowStartTime) { this.windowStartTime = windowStartTime; }

    public int getMaxRetry() { return maxRetry; }

    public void setMaxRetry(int maxRetry) { this.maxRetry = maxRetry; }

    public List<SourceExpectation> getSources() { return sources; }

    public void setSources(List<SourceExpectation> sources) {
        this.sources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
    }


    public static class SourceExpectation {

        private String name;

        private String url;

        private List<String> fileTypes = new ArrayList<>();

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }

        public void setUrl(String url) { this.url = url; }

        public List<String> getFileTypes() { return fileTypes; }

        public void setFileTypes(List<String> fileTypes) {
            this.fileTypes = fileTypes == null ? new ArrayList<>() : new ArrayList<>(fileTypes);
        }
    }
}
