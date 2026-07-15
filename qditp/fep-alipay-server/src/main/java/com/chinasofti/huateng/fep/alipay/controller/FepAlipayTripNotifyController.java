package com.chinasofti.huateng.fep.alipay.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPushTransDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPushTransDataRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveCardDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveCardDataRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListRespDTO;
import com.chinasofti.huateng.fep.alipay.service.AlipayTripService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行通知回调控制器。
 * <p>
 * 统一承接支付宝出行推送的业务通知，包括行程数据推送等。
 * 注意：此处为 ITP 内部触发接口，由 ticket-server / online-server 在行程完成后调用，
 * 再由 fep-alipay-server 主动推送给支付宝出行侧。
 * </p>
 */
@RestController
@RequestMapping("/notify")
public class FepAlipayTripNotifyController {
    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripNotifyController.class);

    private final AlipayTripService alipayTripService;

    public FepAlipayTripNotifyController(AlipayTripService alipayTripService) {
        this.alipayTripService = alipayTripService;
    }

    /**
     * 支付宝出行-行程数据推送。
     */
    @PostMapping("/pushTransData")
    public AlipayTripPushTransDataRespDTO pushTransData(@RequestBody AlipayTripPushTransDataReqDTO request) {
        log.info("接收到支付宝出行-行程数据推送: {}", request);
        AlipayTripPushTransDataRespDTO response = alipayTripService.pushTransData(request);
        log.info("支付宝出行-行程数据推送,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    /**
     * 支付宝出行-业务关闭结果通知。
     */
    @PostMapping("/closeResultForAlipay")
    public AlipayTripCloseResultRespDTO closeResultForAlipay(@RequestBody AlipayTripCloseResultReqDTO request) {
        log.info("接收到支付宝出行-业务关闭结果通知: {}", request);
        AlipayTripCloseResultRespDTO response = alipayTripService.closeResultForAlipay(request);
        log.info("支付宝出行-业务关闭结果通知,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    /**
     * 支付宝出行-行业数据推送。
     */
    @PostMapping("/receiveCardDataFromItp")
    public AlipayTripReceiveCardDataRespDTO receiveCardDataFromItp(@RequestBody AlipayTripReceiveCardDataReqDTO request) {
        log.info("接收到支付宝出行-行业数据推送: {}", request);
        AlipayTripReceiveCardDataRespDTO response = alipayTripService.receiveCardDataFromItp(request);
        log.info("支付宝出行-行业数据推送,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    /**
     * 支付宝出行-黑名单状态变更通知。
     */
    @PostMapping("/receiveBlackListFromItp")
    public AlipayTripReceiveBlackListRespDTO receiveBlackListFromItp(@RequestBody AlipayTripReceiveBlackListReqDTO request) {
        log.info("接收到支付宝出行-黑名单状态变更通知: {}", request);
        AlipayTripReceiveBlackListRespDTO response = alipayTripService.receiveBlackListFromItp(request);
        log.info("支付宝出行-黑名单状态变更通知,响应结果：{}", JSON.toJSONString(response));
        return response;
    }
}
