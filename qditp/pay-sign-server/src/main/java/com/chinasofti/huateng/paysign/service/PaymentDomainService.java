package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;

/**
 * 支付领域入口，负责支付订单的同步受理与支付结果回调收口。
 *
 * <p>退款（发起 + 两套补偿）已于 2026-09-15 拆出为 {@link RefundDomainService}（纯搬迁），
 * <b>NEVER 在本接口上加回退款方法</b>。</p>
 */
public interface PaymentDomainService {
    RequestPayResult requestPay(RequestPayReqDTO request);

    /**
     * 支付 API 5.1 支付结果回调。
     *
     * <p>它落在**支付**领域而不是回调领域：整个方法只读写 {@code PAY_CALLBACK_LOG} /
     * {@code PAY_TXN_DETAIL}，并与 {@code requestPay} 共用 {@code resolveDebitRequestResult}
     * 等支付组私有方法。对外入口仍是 {@code CallbackDomainService#receivePayResult}
     * （{@code PaySignServiceImpl} 的路由一行未动），那一层只做转发。
     * <b>NEVER 把状态回写逻辑复制回回调领域。</b></p>
     */
    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody);
}
