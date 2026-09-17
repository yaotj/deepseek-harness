package com.chinasofti.huateng.model.app;

/**
 * 按卡号查询「是否仍有未结清扣费订单」的通用响应。
 */
public class CardUnsettledQueryRespDTO {

    /** 查询结果码，"0000" 表示查询成功执行。 */
    private String resultCode;

    /** 查询结果描述。 */
    private String resultMsg;

    /** 是否仍有未结清扣费订单。 */
    private boolean hasUnsettled;

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

    public boolean isHasUnsettled() {
        return hasUnsettled;
    }

    public void setHasUnsettled(boolean hasUnsettled) {
        this.hasUnsettled = hasUnsettled;
    }
}
