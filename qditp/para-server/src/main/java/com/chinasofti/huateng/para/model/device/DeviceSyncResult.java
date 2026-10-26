package com.chinasofti.huateng.para.model.device;

/** 一次 ACC 设备台账同步的执行结果汇总。 */
public class DeviceSyncResult {
    /** 本次同步覆盖的 ACC manageLevel 码值，逗号分隔。 */
    private String manageLevels;

    /** 实际请求的分页次数。 */
    private int pages;

    /** 从 ACC 拉取的设备记录数。 */
    private int fetched;

    /** MERGE INTO 落库的设备记录数（含更新与新增）。 */
    private int merged;

    /** 本次同步耗时（毫秒）。 */
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
