package com.chinasofti.huateng.model.pay;

/**
 * 扣费失败订单查询响应。
 */
public class GateTxnPayFailedOrderRespDTO {

    /** 是否存在扣费失败订单。 */
    private boolean hasFailedOrder;

    public boolean isHasFailedOrder() {
        return hasFailedOrder;
    }

    public void setHasFailedOrder(boolean hasFailedOrder) {
        this.hasFailedOrder = hasFailedOrder;
    }
}
