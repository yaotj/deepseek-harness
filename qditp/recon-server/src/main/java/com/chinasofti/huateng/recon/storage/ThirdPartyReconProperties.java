package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;


@ConfigurationProperties(prefix = "recon.thirdparty")
public class ThirdPartyReconProperties {

    private boolean enabled = false;

    private String billLocalDir = "/home/javaapp/app/recon/thirdparty-bill";

    private String billFilePattern = "reconciliation-bill-*.csv";

    private String outputDir = "/home/javaapp/app/recon/txndiff";

    private boolean schedulerEnabled = false;

    private String schedulerCron = "0 30 3 * * ?";

    private long facePayFetchTimeoutMillis = 300000L;

    private Map<String, String> channelMapping = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBillLocalDir() { return billLocalDir; }
    public void setBillLocalDir(String billLocalDir) { this.billLocalDir = billLocalDir; }
    public String getBillFilePattern() { return billFilePattern; }
    public void setBillFilePattern(String billFilePattern) { this.billFilePattern = billFilePattern; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }
    public String getSchedulerCron() { return schedulerCron; }
    public void setSchedulerCron(String schedulerCron) { this.schedulerCron = schedulerCron; }

    public long getFacePayFetchTimeoutMillis() { return facePayFetchTimeoutMillis; }
    public void setFacePayFetchTimeoutMillis(long facePayFetchTimeoutMillis) { this.facePayFetchTimeoutMillis = facePayFetchTimeoutMillis; }
    public Map<String, String> getChannelMapping() { return channelMapping; }
    public void setChannelMapping(Map<String, String> channelMapping) { this.channelMapping = channelMapping == null ? new HashMap<>() : channelMapping; }
}
