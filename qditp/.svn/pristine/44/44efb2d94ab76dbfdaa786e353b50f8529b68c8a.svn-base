package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.rpc.collectpay.CollectPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * APP order request gateway.
 */
@RestController
@RequestMapping("/ci")
public class AppOrderController {
    private static final Logger log = LoggerFactory.getLogger(AppOrderController.class);

    private final CollectPayClient collectPayClient;

    public AppOrderController(CollectPayClient collectPayClient) {
        this.collectPayClient = collectPayClient;
    }

    /**
     * IF8A-20 APP order request. The original FormData envelope is forwarded
     * directly because collect-pay-server binds it with {@code @ModelAttribute}.
     */
    @PostMapping("/app/requestOrder")
    public JSONObject requestOrder(@RequestParam Map<String, String> formData) {
        log.info("IF8A-20 APP request order, formData={}", formData);
        return collectPayClient.requestOrder(formData);
    }

    /**
     * IF8A-11 APP payment information request.
     */
    @PostMapping("/app/requestPaymentInfo")
    public JSONObject requestPaymentInfo(@RequestParam Map<String, String> formData) {
        log.info("IF8A-11 APP request payment information, formData={}", formData);
        return collectPayClient.requestPaymentInfo(formData);
    }

    /**
     * IF8A-18 APP payment result query.
     */
    @PostMapping("/app/requestPayResult")
    public JSONObject requestPayResult(@RequestParam Map<String, String> formData) {
        log.info("IF8A-18 APP request payment result, formData={}", formData);
        return collectPayClient.requestPayResult(formData);
    }

    /**
     * IF8A-12  请求退款
     *
     * @param formData
     * @return
     */
    @PostMapping("/app/requestRefundTicket")
    public JSONObject requestRefundTicket(@RequestParam Map<String, String> formData) {
        log.info("IF8A-12 APP 请求退款, formData={}", formData);
        return collectPayClient.requestRefundTicket(formData);
    }

    /**
     * IF8A-13 退款结果查询
     *
     * @param formData
     * @return
     */
    @PostMapping("/app/requestRefundTicketResult")
    public JSONObject requestRefundTicketResult(@RequestParam Map<String, String> formData) {
        log.info("IF8A-13 APP 退款结果查询, formData={}", formData);
        return collectPayClient.requestRefundTicketResult(formData);
    }

    /**
     *获取激活取票订单
     * @param formData
     * @return
     */
    @PostMapping("/app/requestPreActiveOrderList")
    public JSONObject requestPreActiveOrderList(@RequestParam Map<String, String> formData) {
        log.info("IF8A-14 获取激活取票订单, formData={}", formData);
        return collectPayClient.requestPreActiveOrderList(formData);
    }

    /**
     * 激活取票订单
     * @param formData
     * @return
     */
    @PostMapping("/app/requestActiveTicket")
    public JSONObject requestActiveTicket(@RequestParam Map<String, String> formData) {
        log.info("IF8A-15 激活取票订单, formData={}", formData);
        return collectPayClient.requestActiveTicket(formData);
    }

    /**
     * 支付中心退款结果通知
     * @param formData
     * @return
     */
    @PostMapping("/app/receiveRefundResult")
    public JSONObject receiveRefundResult(@RequestParam Map<String, String> formData) {
        log.info("当面付支付中心退款结果通知, formData={}", formData);
        return collectPayClient.receiveRefundResult(formData);
    }

}
