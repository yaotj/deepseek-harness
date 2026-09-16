package com.chinasofti.huateng.gatetxnpay.model.page;

/**
 * 历史订单 {@code ORIGINAL_FARE}（地铁原价）补数请求参数。
 *
 * <p>{@code ORIGINAL_FARE} 只在出站时由 {@code GateTxnPayServiceImpl.fillOriginalFare} 写一次，
 * para-server 查不到票价时留空且**没有补偿任务**，因此需要本接口按进出站重查后回填。</p>
 *
 * <p>{@code startDate} / {@code endDate} 是 {@code yyyyMMdd} 且 MUST 非空：{@code GATE_TXN_PAY}
 * 是 {@code TXN_DATE} 月分区表，不带区间会扫全部分区。</p>
 */
public class OriginalFareBackfillRequest {
    /** 交易日期下限，yyyyMMdd，必填。 */
    private String startDate;

    /** 交易日期上限，yyyyMMdd，必填。 */
    private String endDate;

    /** 单次最多处理条数，默认 500，上限 5000。每条都要调一次 para-server，NEVER 设过大。 */
    private Integer limit;

    /** 是否只试算不落库，默认 true。确认预览结果后再传 false 实际回填。 */
    private Boolean dryRun;

    /** 可疑差额阈值（分），默认 300。实付大于 0 且「原价 - 实付」超过该值时跳过并列入 suspectList。 */
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
