package com.chinasofti.huateng.para.model.device;

public class DeviceSyncResult {
    private String manageLevels;

    private int pages;

    private int fetched;

    private int merged;

    private long elapsedMillis;

    public String getManageLevels() { return manageLevels; }
    public void setManageLevels(String manageLevels) { this.manageLevels = manageLevels; }
    public int getPages() { return pages; }
    public void setPages(int pages) { this.pages = pages; }
    public int getFetched() { return fetched; }
    public void setFetched(int fetched) { this.fetched = fetched; }
    public int getMerged() { return merged; }
    public void setMerged(int merged) { this.merged = merged; }
    public long getElapsedMillis() { return elapsedMillis; }
    public void setElapsedMillis(long elapsedMillis) { this.elapsedMillis = elapsedMillis; }
}
