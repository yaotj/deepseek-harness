package com.chinasofti.huateng.facepay.service.supplement;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderRespDTO;

public interface SupplementOrderService {

    String ORDER_NO_PREFIX = "SP";

    SupplementOrderRespDTO requestPayOrder(SupplementOrderReqDTO request);

    JSONObject requestPayInfo(RequestPayInfoReqDTO request);

    boolean isSupplementOrder(String orderNo);

    int convergePendingOrders(int limit);

    int closeTimeoutOrders(int timeoutMinutes, int limit);

    /** PROCESSING 超时关单（本地兜底，不依赖支付中心可达）。 */
    int closeStaleProcessingOrders(int timeoutMinutes, int limit);
}
