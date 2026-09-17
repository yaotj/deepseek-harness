package com.chinasofti.huateng.gatetxnpay.controller;

import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayQueryService;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 过闸扣费交易入口——服务间 RPC 调用方向（fep-dev-server / pay-sign-server / blacklist-server / ticket-server 等）。
 */
@RestController
@RequestMapping("/ci/gateTxnPay")
public class GateTxnPayController {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayController.class);

    @Autowired
    private GateTxnPayService gateTxnPayService;

    /** 只读查询侧。 */
    @Autowired
    private GateTxnPayQueryService gateTxnPayQueryService;

    @PostMapping("/requestPay")
    public GateTxnPayRespDTO requestPay(@RequestBody GateTxnPayReqDTO request) {
        log.info("接收到过闸扣费交易, 入参={}", request);
        GateTxnPayRespDTO response = gateTxnPayService.requestPay(request);
        log.info("过闸扣费交易处理完成, 返回={}", response);
        return response;
    }

    @PostMapping("/retryPay")
    public GateTxnPayRespDTO retryPay(@RequestBody Map<String, String> request) {
        String orderNo = request != null ? request.get("orderNo") : null;
        log.info("接收到过闸扣费重试支付, orderNo={}", orderNo);
        GateTxnPayRespDTO response = gateTxnPayService.retryPay(orderNo);
        log.info("过闸扣费重试支付处理完成, 返回={}", response);
        return response;
    }

    @PostMapping("/queryOrderByBizKey")
    public GateTxnPayRespDTO queryOrderByBizKey(@RequestBody GateTxnPayReqDTO request) {
        log.info("查询GT订单号, 入参={}", request);
        GateTxnPayRespDTO response = gateTxnPayQueryService.queryOrderByBizKey(request);
        log.info("查询GT订单号完成, 返回={}", response);
        return response;
    }

    @PostMapping("/hasFailedOrder")
    public GateTxnPayFailedOrderRespDTO hasFailedOrder(@RequestBody GateTxnPayFailedOrderReqDTO request) {
        log.info("查询解约扣费失败订单, 入参={}", request);
        GateTxnPayFailedOrderRespDTO response = gateTxnPayQueryService.hasFailedOrder(
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getPaymentVendor() : null,
                request != null ? request.getRequestTime() : null);
        log.info("查询解约扣费失败订单完成, 返回={}", response);
        return response;
    }

    /** 按卡号查询是否仍有未结清扣费订单（供 blacklist-server 盘点黑名单可解除性调用）。 */
    @PostMapping("/hasUnsettledOrderByCard")
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        log.info("按卡查询未结清扣费订单, cardId={}", request != null ? request.getCardId() : null);
        CardUnsettledQueryRespDTO response = gateTxnPayQueryService.hasUnsettledOrderByCard(
                request != null ? request.getCardId() : null);
        log.info("按卡查询未结清扣费订单完成, 返回={}", response);
        return response;
    }

    /** pay-sign-server 收到支付中心回调、本地 PAY_TXN_DETAIL 落地成功后调用，把 GATE_TXN_PAY.DEBIT_STATUS 收敛到终态。 */
    @PostMapping("/syncDebitStatus")
    public GateTxnPayRespDTO syncDebitStatus(@RequestBody GateTxnPaySyncStatusReqDTO request) {
        log.info("接收支付结果同步, 入参={}", request);
        GateTxnPayRespDTO response = gateTxnPayService.syncDebitStatus(request);
        log.info("支付结果同步返回={}", response);
        return response;
    }
}
