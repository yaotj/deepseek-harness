package com.chinasofti.huateng.online.controller;

import com.chinasofti.huateng.online.model.BaseRespDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos;
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
 * ITP 与 TVM 对接控制器。
 * 对应规范第 7.5 章，负责单程票下单、充值下单、支付结果查询、
 * 出票结果通知、充值结果通知、扫码取票订单查询和设备心跳。
 */
@RestController
@RequestMapping("/ci/tvm")
public class TvmController {
    private static final Logger log = LoggerFactory.getLogger(TvmController.class);

    @Autowired
    private OnlineService onlineService;

    /**
     * IF2A-01 提交单程票订单。
     * TVM 现场购票下单后，由 ITP 返回订单号和支付二维码地址。
     */
    @PostMapping("/requestGenSjtOrder")
    public TvmDtos.RequestGenSjtOrderRespDTO requestGenSjtOrder(
            @RequestBody TvmDtos.RequestGenSjtOrderReqDTO request,
            @RequestParam(value = "deviceId", required = false) String deviceId) {
        log.info("接收TVM单程票下单请求");
        return onlineService.requestGenSjtOrder(request, deviceId);
    }

    /**
     * IF2A-03 查询支付结果。
     * TVM 在二维码展示期间循环调用，直到支付成功、失败或超时。
     */
    @PostMapping("/requestPayResult")
    public TvmDtos.RequestPayResultRespDTO requestPayResult(@RequestBody TvmDtos.RequestPayResultReqDTO request) {
        log.info("接收TVM支付结果查询: {}", request.getOrderNo());
        return onlineService.requestTvmPayResult(request);
    }

    /**
     * IF2A-04 出票结果通知。
     * TVM 成功出票后通知平台落库出票结果与票卡明细。
     */
    @PostMapping("/notiTakeTicketResult")
    public BaseRespDTO notiTakeTicketResult(@RequestBody TvmDtos.NotiTakeTicketResultReqDTO request) {
        log.info("接收TVM出票成功通知: {}", request.getOrderNo());
        return onlineService.notiTakeTicketResult(request);
    }

    /**
     * IF2A-05 出票故障通知。
     * TVM 出票张数与订单张数不一致或发生故障时回告平台。
     */
    @PostMapping("/notiTakeTicketFailResult")
    public BaseRespDTO notiTakeTicketFailResult(@RequestBody TvmDtos.NotiTakeTicketFailResultReqDTO request) {
        log.info("接收TVM出票故障通知: {}", request.getOrderNo());
        return onlineService.notiTakeTicketFailResult(request);
    }

    /**
     * IF2A-06 充值结果通知。
     * TVM 实体卡充值成功后通知平台更新订单结果。
     */
    @PostMapping("/topupCardResultNoti")
    public BaseRespDTO topupCardResultNoti(@RequestBody TvmDtos.TopupCardResultNotiReqDTO request) {
        log.info("接收TVM充值成功通知: {}", request.getOrderNo());
        return onlineService.topupCardResultNoti(request);
    }

    /**
     * IF2A-07 充值失败通知。
     * TVM 充值失败、取消或存疑时通知平台做订单状态收敛。
     */
    @PostMapping("/topupCardFailNoti")
    public BaseRespDTO topupCardFailNoti(@RequestBody TvmDtos.TopupCardFailNotiReqDTO request) {
        log.info("接收TVM充值失败通知: {}", request.getOrderNo());
        return onlineService.topupCardFailNoti(request);
    }

    /**
     * IF2A-08 扫码取票订单查询。
     * TVM 根据自身展示的取票二维码向平台查询是否存在可出票的激活订单。
     */
    @PostMapping("/requestTakeTicketAuth")
    public TvmDtos.RequestTakeTicketAuthRespDTO requestTakeTicketAuth(
            @RequestBody TvmDtos.RequestTakeTicketAuthReqDTO request) {
        log.info("接收TVM扫码取票订单查询");
        return onlineService.requestTakeTicketAuth(request);
    }

    /**
     * IF2A-09 请求充值下单。
     * TVM 实体卡充值前生成待支付订单并返回二维码支付地址。
     */
    @PostMapping("/requestTopup")
    public TvmDtos.RequestTopupRespDTO requestTopup(@RequestBody TvmDtos.RequestTopupReqDTO request,
                                                    @RequestParam(value = "deviceId", required = false) String deviceId) {
        log.info("接收TVM充值下单请求");
        return onlineService.requestTopup(request, deviceId);
    }

    /**
     * IF2A-10 设备心跳。
     * TVM 设备待机期间定时上送心跳，平台用于记录设备在线情况。
     */
    @PostMapping("/notiDeviceHeard")
    public BaseRespDTO notiDeviceHeard(@RequestParam(value = "deviceId", required = false) String deviceId) {
        log.info("接收TVM设备心跳: {}", deviceId);
        return onlineService.deviceHeartbeat("02", deviceId);
    }

    /**
     * IF2A-11 扫码支付。
     * TVM 扫描用户付款码后发起支付，平台返回同步支付结果。
     */
    @PostMapping("/requestPayment")
    public TvmDtos.RequestPaymentRespDTO requestPayment(@RequestBody TvmDtos.RequestPaymentReqDTO request) {
        log.info("接收TVM扫码支付请求: {}", request.getOrderNo());
        return onlineService.requestTvmPayment(request);
    }
}
