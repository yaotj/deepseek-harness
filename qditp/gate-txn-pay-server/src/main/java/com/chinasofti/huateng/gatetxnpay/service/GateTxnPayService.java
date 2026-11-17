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

public interface GateTxnPayService {
    GateTxnPayRespDTO requestPay(GateTxnPayReqDTO request);


    GateTxnPayRespDTO retryPay(String orderNo);

    ResultVO<RequestRefundResult> requestRefund(String orderNo, GateTxnPayRefundRequest request);

    ResultVO<BatchRefundResult> batchRefundOvertime(BatchRefundOvertimeRequest request);

    GateTxnPayRespDTO syncDebitStatus(GateTxnPaySyncStatusReqDTO request);


    GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(GateTxnPayDebitConvergeReqDTO request);
}
