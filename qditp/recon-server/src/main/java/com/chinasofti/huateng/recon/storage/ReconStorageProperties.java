package com.chinasofti.huateng.recon.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "recon")
public class ReconStorageProperties {
    private String storageRoot = "/home/javaapp/app/recon";
    private long maxPartBytes = 134217728L;
    private long maxPartRecords = 250000L;
    private String internalToken;
    private List<String> sources = new ArrayList<>(List.of("gate-txn-pay", "ticket", "collect-pay", "daily-ticket"));

    public String getStorageRoot() { return storageRoot; }
    public void setStorageRoot(String storageRoot) { this.storageRoot = storageRoot; }
    public long getMaxPartBytes() { return maxPartBytes; }
    public void setMaxPartBytes(long maxPartBytes) { this.maxPartBytes = maxPartBytes; }
    public long getMaxPartRecords() { return maxPartRecords; }
    public void setMaxPartRecords(long maxPartRecords) { this.maxPartRecords = maxPartRecords; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources == null ? new ArrayList<>() : new ArrayList<>(sources); }
}
