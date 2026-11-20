package com.chinasofti.huateng.model.app;


/**
 * 按 cardId 统计扣费失败订单数（DEBIT_STATUS IN FAIL/RETRY，全量、不分渠道、无时间窗口）。
 * 供 blacklist-server 的 queryBlackList 回填 failedCount（IT-087）：APP 拉码前查黑名单时一并拿到该卡欠费失败笔数。
 * 入参复用 CardUnsettledQueryReqDTO（cardId）。
 */
public class CardFailedCountRespDTO {

    private String resultCode;

    private String resultMsg;

    private int failedCount;

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }
}
