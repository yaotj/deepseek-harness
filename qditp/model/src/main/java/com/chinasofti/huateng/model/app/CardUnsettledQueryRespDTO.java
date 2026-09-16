package com.chinasofti.huateng.model.app;

/**
 * 按卡号查询「是否仍有未结清扣费订单」的通用响应。
 *
 * <p>调用方 MUST 先判断 {@code resultCode} 是否为 "0000"，再使用 {@code hasUnsettled}。
 * 查询未真正执行时（参数缺失、下游异常）{@code hasUnsettled} 固定为 {@code true}，
 * 避免调用方漏判 resultCode 时把「查不到」当成「已结清」而误放行。</p>
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
