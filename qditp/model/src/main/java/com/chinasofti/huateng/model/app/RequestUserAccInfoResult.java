package com.chinasofti.huateng.model.app;

/**
 * IF8A-35 查询用户账务信息应答。
 */
public class RequestUserAccInfoResult {

    /** 返回码，"0000" 表示查询成功执行。 */
    private String retCode;

    /** 返回消息。 */
    private String retMsg;

    /** 未支付订单数（DEBIT_STATUS 为 INIT / PROCESSING）。 */
    private int unpaidCount;

    /** 扣费失败订单数（DEBIT_STATUS 为 FAIL / RETRY）。 */
    private int failureCount;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public int getUnpaidCount() {
        return unpaidCount;
    }

    public void setUnpaidCount(int unpaidCount) {
        this.unpaidCount = unpaidCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    @Override
    public String toString() {
        return "RequestUserAccInfoResult{retCode='" + retCode + "', retMsg='" + retMsg
                + "', unpaidCount=" + unpaidCount + ", failureCount=" + failureCount + '}';
    }
}
