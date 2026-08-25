package com.chinasofti.huateng.gatetxnpay.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * 过闸扣费交易入口。
 */
@RestController
@RequestMapping("/ci/gateTxnPay")
public class GateTxnPayController {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayController.class);

    @Autowired
    private GateTxnPayService gateTxnPayService;

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
        GateTxnPayRespDTO response = gateTxnPayService.queryOrderByBizKey(request);
        log.info("查询GT订单号完成, 返回={}", response);
        return response;
    }

    // ==================== IF8A-05 APP 交易记录列表 RPC ====================

    @PostMapping("/app/requestTransList")
    public List<GateTxnPayListDTO> requestTransList(@RequestBody QueryTransListReqDTO request) {
        log.info("APP查询交易记录列表, 入参={}", request);
        List<GateTxnPayListDTO> list = gateTxnPayService.selectTransList(
                request.getThirdUserId(),
                request.getCardIdList(),
                request.getCardType(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTicketCode(),
                request.getOffset(),
                request.getLimit());
        log.info("APP查询交易记录列表完成, 返回{}条", list == null ? 0 : list.size());
        return list;
    }

    @PostMapping("/app/countTransList")
    public int countTransList(@RequestBody QueryTransListReqDTO request) {
        log.info("APP统计交易记录总数, 入参={}", request);
        int count = gateTxnPayService.countTransList(
                request.getThirdUserId(),
                request.getCardIdList(),
                request.getCardType(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTicketCode());
        log.info("APP统计交易记录总数完成, 返回={}", count);
        return count;
    }

    // ==================== IF8A-34 APP 订单详情 RPC ====================

    @PostMapping("/app/queryByOrderNo")
    public GateTxnPayListDTO queryByOrderNo(@RequestBody Map<String, String> request) {
        String orderNo = request != null ? request.get("orderNo") : null;
        log.info("APP查询订单详情, orderNo={}", orderNo);
        GateTxnPayListDTO dto = gateTxnPayService.selectByOrderNo(orderNo);
        log.info("APP查询订单详情完成, 返回={}", dto != null ? dto.getOrderNo() : null);
        return dto;
    }

    // ==================== 解约扣费失败订单查询 RPC ====================

    @PostMapping("/hasFailedOrder")
    public GateTxnPayFailedOrderRespDTO hasFailedOrder(@RequestBody GateTxnPayFailedOrderReqDTO request) {
        log.info("查询解约扣费失败订单, 入参={}", request);
        GateTxnPayFailedOrderRespDTO response = gateTxnPayService.hasFailedOrder(
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getPaymentVendor() : null,
                request != null ? request.getRequestTime() : null);
        log.info("查询解约扣费失败订单完成, 返回={}", response);
        return response;
    }
}
