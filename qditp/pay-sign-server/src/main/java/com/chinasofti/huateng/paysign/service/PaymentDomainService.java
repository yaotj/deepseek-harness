package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;

/** 支付领域入口，负责支付订单的同步受理与支付结果回调收口。 */
public interface PaymentDomainService {
    RequestPayResult requestPay(RequestPayReqDTO request);

    /** 支付 API 5.1 支付结果回调。 */
    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody);

    /**
     * 登记一条「已完成、不经支付中心」的支付流水（BOM 补站等现场已收款的订单）。
     *
     * <p><b>NEVER 在本方法里出网</b>：它存在的全部理由就是「这笔钱不由 ITP 收」，
     * 一旦调了支付中心就等于对乘客重复收费（ADR-D136）。
     */
    BaseRespDTO registerCompletedTxn(RegisterCompletedPayTxnReqDTO request);
}
