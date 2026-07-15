package com.chinasofti.huateng.online.controller;

import com.chinasofti.huateng.online.model.BaseRespDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos;
import com.chinasofti.huateng.online.service.OnlineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ITP 与 BOM 对接控制器。
 * 对应规范第 7.3 章，统一承接 BOM 侧的二维码更新、非现金业务、充值结果回告、
 * HCE 更新通知以及设备心跳等接口请求。
 */
@RestController
@RequestMapping("/ci/bom")
public class BomController {
    private static final Logger log = LoggerFactory.getLogger(BomController.class);

    @Autowired
    private OnlineService onlineService;

    /**
     * IF5A-01 请求票卡分析。
     * BOM 在做二维码补站、更新前先调用本接口，获取平台建议动作。
     */
    @PostMapping("/requestCardDataAnalyse")
    public BomDtos.RequestCardDataAnalyseRespDTO requestCardDataAnalyse(
            @RequestBody BomDtos.RequestCardDataAnalyseReqDTO request) {
        log.info("接收BOM票卡分析请求: {}", request.getCardId());
        return onlineService.requestCardDataAnalyse(request);
    }

    /**
     * IF5A-03 请求票卡更新。
     * BOM 根据票卡分析结果执行补进站/补出站后，调用本接口同步更新平台票卡状态。
     */
    @PostMapping("/requestUpdateCardData")
    public BomDtos.RequestUpdateCardDataRespDTO requestUpdateCardData(
            @RequestBody BomDtos.RequestUpdateCardDataReqDTO request) {
        log.info("接收BOM票卡更新请求: {}", request.getCardId());
        return onlineService.requestUpdateCardData(request);
    }

    /**
     * IF8A-04 请求非现金收款下单。
     * 用于 BOM 行政处理、更新处理、退票、充值等需要扫码支付的业务场景。
     */
    @PostMapping("/requestGenNoCashOrder")
    public BomDtos.RequestGenNoCashOrderRespDTO requestGenNoCashOrder(
            @RequestBody BomDtos.RequestGenNoCashOrderReqDTO request,
            @RequestParam(value = "deviceId", required = false) String deviceId) {
        log.info("接收BOM非现金下单请求");
        return onlineService.requestGenNoCashOrder(request, deviceId);
    }

    /**
     * IF8A-05 扫码支付。
     * BOM 扫描用户付款码后调用，平台返回同步支付结果。
     */
    @PostMapping("/requestPayment")
    public BomDtos.RequestPaymentRespDTO requestPayment(@RequestBody BomDtos.RequestPaymentReqDTO request) {
        log.info("接收BOM扫码支付请求: {}", request.getOrderNo());
        return onlineService.requestBomPayment(request);
    }

    /**
     * IF2A-09 BOM 上报充值结果通知。
     * 用于 TVM 充值存疑后，乘客到 BOM 处理完成后的最终结果回告。
     */
    @PostMapping("/notiTopupResult")
    public BaseRespDTO notiTopupResult(@RequestBody BomDtos.BomTopupResultReqDTO request) {
        log.info("接收BOM充值结果通知: {}", request.getOrderNo());
        return onlineService.notiBomTopupResult(request);
    }

    /**
     * IF8A-06 查询支付结果。
     * 当 BOM 的扫码支付同步结果超时或不明确时，通过本接口轮询最终支付状态。
     */
    @PostMapping("/requestGetPayResult")
    public BomDtos.RequestGetPayResultRespDTO requestGetPayResult(
            @RequestBody BomDtos.RequestGetPayResultReqDTO request) {
        log.info("接收BOM支付结果查询: {}", request.getOrderNo());
        return onlineService.requestBomPayResult(request);
    }

    /**
     * IF2A-07 设备心跳。
     * BOM 待机时定时上送心跳，平台据此记录设备在线状态。
     */
    @PostMapping("/notiDeviceHeard")
    public BaseRespDTO notiDeviceHeard(@RequestParam(value = "deviceId", required = false) String deviceId) {
        log.info("接收BOM设备心跳: {}", deviceId);
        return onlineService.deviceHeartbeat("03", deviceId);
    }

    /**
     * IF2A-08 业务操作结果通知。
     * BOM 本地业务结束后回告平台，用于订单闭环和后续对账。
     */
    @PostMapping("/notiBusResult")
    public BaseRespDTO notiBusResult(@RequestBody BomDtos.BomBusinessResultReqDTO request) {
        log.info("接收BOM业务结果通知: {}", request.getOrderNo());
        return onlineService.notiBomBusinessResult(request);
    }

    /**
     * IF5A-09 HCE 票卡更新结果通知。
     * BOM 更新 HCE 票卡完成后，将最新 HCE 数据和处理结果同步给平台。
     */
    @PostMapping("/notiUpdateHceData")
    public BaseRespDTO notiUpdateHceData(@RequestBody BomDtos.HceUpdateResultReqDTO request) {
        log.info("接收BOM HCE更新结果通知: {}", request.getCardId());
        return onlineService.notiUpdateHceData(request);
    }
}
