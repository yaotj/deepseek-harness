package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.rpc.collectpay.CollectPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * APP 订单支付接口入口。
 */
@RestController
public class CollectPayController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(CollectPayController.class);

    private final CollectPayClient collectPayClient;

    public CollectPayController(CollectPayClient collectPayClient) {
        this.collectPayClient = collectPayClient;
    }

    @PostMapping({"/ci/app/requestOrder"})
    public JSONObject requestOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-20 请求下单, request={}", request);
        return collectPayClient.requestOrder(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestPaymentInfo"})
    public JSONObject requestPaymentInfo(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-11 请求支付信息, request={}", request);
        return collectPayClient.requestPaymentInfo(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestPayResult"})
    public JSONObject requestPayResult(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-18 支付结果查询, request={}", request);
        return collectPayClient.requestPayResult(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestRefundTicket"})
    public JSONObject requestRefundTicket(@ModelAttribute ItpCommonFormRequest request) {
        log.info("请求退款, request={}", request);
        return collectPayClient.requestRefundTicket(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestRefundTicketResult"})
    public JSONObject requestRefundTicketResult(@ModelAttribute ItpCommonFormRequest request) {
        log.info("退款结果查询, request={}", request);
        return collectPayClient.requestRefundTicketResult(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestPreActiveOrderList"})
    public JSONObject requestPreActiveOrderList(@ModelAttribute ItpCommonFormRequest request) {
        log.info("查询激活订单列表, request={}", request);
        return collectPayClient.requestPreActiveOrderList(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestActiveTicket"})
    public JSONObject requestActiveTicket(@ModelAttribute ItpCommonFormRequest request) {
        log.info("TVM激活请求, request={}", request);
        return collectPayClient.requestActiveTicket(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/receiveRefundResult"})
    public JSONObject receiveRefundResult(@ModelAttribute ItpCommonFormRequest request) {
        log.info("接收退款结果通知, request={}", request);
        return collectPayClient.receiveRefundResult(toFormDataMap(request));
    }

    private Map<String, String> toFormDataMap(ItpCommonFormRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("providerId", request.getProviderId());
        map.put("charset", request.getCharset());
        map.put("format", request.getFormat());
        map.put("timestamp", request.getTimestamp());
        map.put("deviceId", request.getDeviceId());
        map.put("signType", request.getSignType());
        map.put("sign", request.getSign());
        map.put("bizData", request.getBizData());
        return map;
    }
}
