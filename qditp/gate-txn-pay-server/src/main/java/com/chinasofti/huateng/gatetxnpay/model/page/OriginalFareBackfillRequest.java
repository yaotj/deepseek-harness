package com.chinasofti.huateng.gatetxnpay.model.page;

public class OriginalFareBackfillRequest {
    private String startDate;

    private String endDate;

    private Integer limit;

    private Boolean dryRun;

    private Integer suspectDiffCents;

    private Boolean force;

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Integer getSuspectDiffCents() {
        return suspectDiffCents;
    }

    public void setSuspectDiffCents(Integer suspectDiffCents) {
        this.suspectDiffCents = suspectDiffCents;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }
}
