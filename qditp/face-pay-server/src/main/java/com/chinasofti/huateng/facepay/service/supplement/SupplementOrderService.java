package com.chinasofti.huateng.facepay.service.supplement;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderRespDTO;

public interface SupplementOrderService {

    /**
     * 补款单号前缀。IF8A-11 靠它把补款单与取票单分流，
     * 落单侧 {@code generateOrderNo} 用的是同一个常量，<b>两处 MUST 同源</b>。
     */
    String ORDER_NO_PREFIX = "SP";

    SupplementOrderRespDTO requestPayOrder(SupplementOrderReqDTO request);

    /**
     * IF8A-11 请求支付信息（补款单分支）。
     *
     * <p>APP 拿到 IF8A-26 的 {@code orderNo} 后仍会调 {@code /ci/app/requestPaymentInfo}，
     * 而那条链路只查 {@code F2F_ORDER}、补款单落在 {@code SUPPLEMENT_ORDER} ⇒ 恒返
     * {@code 9999 订单号错误}（2026-09-16 实测，APP 侧表现为「生成订单失败」）。
     * 应答形态与取票单逐字一致（{@code payChannelCode / paymentInfo / signType / sign}），
     * <b>APP 侧无需改动</b>。</p>
     */
    JSONObject requestPayInfo(RequestPayInfoReqDTO request);

    boolean isSupplementOrder(String orderNo);

    int convergePendingOrders(int limit);

    int closeTimeoutOrders(int timeoutMinutes, int limit);
}
