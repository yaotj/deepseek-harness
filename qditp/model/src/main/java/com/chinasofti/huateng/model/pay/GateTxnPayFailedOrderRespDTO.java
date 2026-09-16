package com.chinasofti.huateng.model.pay;

/**
 * 未结清扣费订单查询响应。
 *
 * 调用方（解约流程）MUST 先判断 resultCode 是否为 "0000"，再使用 hasFailedOrder。
 * 查询未真正执行时 hasFailedOrder 固定为 true，避免调用方漏判 resultCode 时误放行解约。
 */
public class GateTxnPayFailedOrderRespDTO {

    /** 查询结果码，"0000" 表示查询成功执行。 */
    private String resultCode;

    /** 查询结果描述。 */
    private String resultMsg;

    /** 是否存在未结清扣费订单（DEBIT_STATUS != 'SUCCESS'）。 */
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
