package com.chinasofti.huateng.paysign.model.response;

/** 查询扣费失败订单内部接口响应。 */
public class CheckFailedOrdersRespDTO {

    private String resultCode;
    private String resultMsg;
    private boolean hasFailedOrder;

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

    public boolean isHasFailedOrder() {
        return hasFailedOrder;
    }

    public void setHasFailedOrder(boolean hasFailedOrder) {
        this.hasFailedOrder = hasFailedOrder;
    }
}
