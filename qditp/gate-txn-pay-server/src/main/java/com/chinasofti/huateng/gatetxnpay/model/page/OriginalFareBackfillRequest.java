package com.chinasofti.huateng.gatetxnpay.model.page;

/** 历史订单 {@code ORIGINAL_FARE}（地铁原价）补数请求参数。 */
public class OriginalFareBackfillRequest {
    /** 交易日期下限，yyyyMMdd，必填。 */
    private String startDate;

    /** 交易日期上限，yyyyMMdd，必填。 */
    private String endDate;

    /** 单次最多处理条数，默认 500，上限 5000。 */
    private Integer limit;

    /** 是否只试算不落库，默认 true。 */
    private Boolean dryRun;

    /** 可疑差额阈值（分），默认 300。 */
    private Integer suspectDiffCents;

    /** 是否忽略可疑差额阈值强制回填，默认 false。 */
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
