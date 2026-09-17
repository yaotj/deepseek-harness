package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundOvertimeRequest;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundResult;
import com.chinasofti.huateng.gatetxnpay.model.page.GateTxnPayRefundRequest;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;

/** `GATE_TXN_PAY` 的写入侧：出站扣费、重试、退款、状态收敛、离线码金额补偿重算。 */
public interface GateTxnPayService {
    GateTxnPayRespDTO requestPay(GateTxnPayReqDTO request);

    /**
     * 对支付失败/未支付的订单单独重试支付。
     *
     * @param orderNo 订单号。
     * @return 支付结果。
     */
    GateTxnPayRespDTO retryPay(String orderNo);

    ResultVO<RequestRefundResult> requestRefund(String orderNo, GateTxnPayRefundRequest request);

    /** 综管台批量退超时罚金：对圈出订单逐单发起退款，金额为各自 {@code OVERTIME_AMOUNT}。 */
    ResultVO<BatchRefundResult> batchRefundOvertime(BatchRefundOvertimeRequest request);

    /** 按支付结果回调收敛扣费状态（供 pay-sign-server RPC 调用）。 */
    GateTxnPayRespDTO syncDebitStatus(GateTxnPaySyncStatusReqDTO request);

    /**
     * 在线补款支付成功后收敛原行程的扣费状态（供 face-pay-server 经 {@code POST /internal/gate-txn-pay/debit/converge} 调用，2026-09-16 新增）。
     */
    GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(GateTxnPayDebitConvergeReqDTO request);
}
