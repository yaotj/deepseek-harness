package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveRefundResultReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;

/** 退款领域入口：退款发起（支付 API 3.1）、退款回调（§5.2）、只读回查与两套退款补偿。 */
public interface RefundDomainService {

    /** 支付 API 3.1 请求退款。 */
    RequestRefundResult requestRefund(RequestRefundReqDTO request);

    /**
     * 支付中心网关 §5.2 退款回调（2026-09-22 新增，P1-3）。
     *
     * <p>与 {@link #compensateRefundQuery()} 是**同一个收口口径的两条路**：回调是快速路径、回查是兜底，
     * 两者复用同一条 {@code finishFromQuery} CAS，**NEVER 给回调另写一条 UPDATE**。
     */
    PaySignCallbackResult receiveRefundResult(ReceiveRefundResultReqDTO request);

    /**
     * 只读回查支付中心 §3.2 退款查询，用于给「我方账不平」的退款单逐笔定性。
     *
     * <p>**只读：NEVER 在本方法里写任何一张表**。要改状态走 {@link #compensateRefundQuery()}。</p>
     */
    BaseRespDTO queryRefundResult(String refundOrderNo);

    /** 退款回查补偿：扫一批停在 {@code PROCESSING} 的 {@code PAY_REFUND_DETAIL}。 */
    CompensateNotifyRespDTO compensateRefundQuery();

    /** 退款汇总跨表对账补偿：扫一批 {@code PAY_REFUND_DETAIL}（唯一账本）与。 */
    CompensateNotifyRespDTO compensateRefundSummary();
}
