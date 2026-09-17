package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;

/** 支付域看**支付中心免密扣款方向**的窄接口（防腐层，2026-09-16，ADR-D113 续）。 */
public interface PaymentGatewayPort {

    /** 支付 API 1.1 免密扣款。 */
    PaymentReply requestPay(RequestPayReqDTO request);

    /** 支付 API 查询支付状态，**只用于「拉黑前二次确认」**。 */
    GatewayReply queryPayStatus(String merchantOrderNo);
}
