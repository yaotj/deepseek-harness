package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.service.AlipayTripService;
import com.chinasofti.huateng.model.alipaytrip.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行通知回调控制器。
 * <p>
 * 统一承接支付宝出行推送的业务通知，包括关闭结果、行程数据、行业数据、黑名单变更等。
 * </p>
 */
@RestController
@RequestMapping("/notify")
public class FepAlipayTripNotifyController {
    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripNotifyController.class);

    @Autowired
    private AlipayTripService alipayTripService;

    /**
     * 1.7 支付宝出行-业务关闭结果通知。
     */
    @PostMapping("/closeResultForAlipay")
    public AlipayTripCloseResultRespDTO closeResultForAlipay(@RequestBody AlipayTripCloseResultReqDTO request) {
        log.info("接收到支付宝出行-业务关闭结果通知: {}", request);
        return alipayTripService.closeResultForAlipay(request);
    }

    /**
     * 1.8 支付宝出行-行程数据推送。
     */
    @PostMapping("/pushTransData")
    public AlipayTripPushTransDataRespDTO pushTransData(@RequestBody AlipayTripPushTransDataReqDTO request) {
        log.info("接收到支付宝出行-行程数据推送: {}", request);
        return alipayTripService.pushTransData(request);
    }

    /**
     * 1.9 支付宝出行-行业数据推送。
     */
    @PostMapping("/receiveCardDataFromItp")
    public AlipayTripReceiveCardDataRespDTO receiveCardDataFromItp(@RequestBody AlipayTripReceiveCardDataReqDTO request) {
        log.info("接收到支付宝出行-行业数据推送: {}", request);
        return alipayTripService.receiveCardDataFromItp(request);
    }

    /**
     * 1.10 支付宝出行-黑名单状态变更通知。
     */
    @PostMapping("/receiveBlackListFromItp")
    public AlipayTripReceiveBlackListRespDTO receiveBlackListFromItp(@RequestBody AlipayTripReceiveBlackListReqDTO request) {
        log.info("接收到支付宝出行-黑名单状态变更通知: {}", request);
        return alipayTripService.receiveBlackListFromItp(request);
    }
}
