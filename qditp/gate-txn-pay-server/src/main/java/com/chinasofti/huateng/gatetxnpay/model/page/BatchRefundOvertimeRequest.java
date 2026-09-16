package com.chinasofti.huateng.gatetxnpay.model.page;

import java.util.List;

/**
 * 综管台批量退超时罚金的请求体。
 *
 * <p>单批笔数上限 {@link #MAX_BATCH_SIZE}：逐单各发起一次支付中心 RPC，笔数越多
 * 接口占用越长，200 是防长事务/超时的硬闸。运营要退更多需分批提交。</p>
 */
public class BatchRefundOvertimeRequest {
    /** 单批最多 200 笔，超出直接拒绝。 */
    public static final int MAX_BATCH_SIZE = 200;

    /** 待退的订单号列表，非空且 ≤ {@link #MAX_BATCH_SIZE}。 */
    private List<String> orderNos;
    /** 退款原因，可选，缺省用默认文案。 */
    private String refundReason;

    public List<String> getOrderNos() {
        return orderNos;
    }

    public void setOrderNos(List<String> orderNos) {
        this.orderNos = orderNos;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }
}
