package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;

/** 退款领域入口：退款发起（支付 API 3.1）与两套退款补偿。 */
public interface RefundDomainService {

    /** 支付 API 3.1 请求退款。 */
    RequestRefundResult requestRefund(RequestRefundReqDTO request);

    /** 退款回查补偿：扫一批停在 {@code PROCESSING} 的 {@code PAY_REFUND_DETAIL}。 */
    CompensateNotifyRespDTO compensateRefundQuery();

    /** 退款汇总跨表对账补偿：扫一批 {@code PAY_REFUND_DETAIL}（唯一账本）与。 */
    CompensateNotifyRespDTO compensateRefundSummary();
}
