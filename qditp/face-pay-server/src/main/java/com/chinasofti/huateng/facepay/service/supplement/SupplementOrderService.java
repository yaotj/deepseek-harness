package com.chinasofti.huateng.facepay.service.supplement;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderRespDTO;

public interface SupplementOrderService {

    /** 补款单号前缀。 */
    String ORDER_NO_PREFIX = "SP";

    SupplementOrderRespDTO requestPayOrder(SupplementOrderReqDTO request);

    /** IF8A-11 请求支付信息（补款单分支）。 */
    JSONObject requestPayInfo(RequestPayInfoReqDTO request);

    boolean isSupplementOrder(String orderNo);

    int convergePendingOrders(int limit);

    int closeTimeoutOrders(int timeoutMinutes, int limit);
}
