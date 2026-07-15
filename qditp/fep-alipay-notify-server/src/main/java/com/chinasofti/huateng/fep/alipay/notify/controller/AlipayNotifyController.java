package com.chinasofti.huateng.fep.alipay.notify.controller;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.fep.alipay.notify.service.AlipayNotifyService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPushTransDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveCardDataReqDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝通知推送 Controller。
 */
@RestController
@RequestMapping("/notify")
public class AlipayNotifyController {

    private final AlipayNotifyService alipayNotifyService;

    public AlipayNotifyController(AlipayNotifyService alipayNotifyService) {
        this.alipayNotifyService = alipayNotifyService;
    }

    /**
     * 业务关闭结果通知。
     */
    @PostMapping("/closeResultForAlipay")
    public AlipayCommonResponse closeResultForAlipay(@RequestBody AlipayTripCloseResultReqDTO dto) {
        return alipayNotifyService.notifyCloseResult(null, dto)
                ? AlipayCommonResponse.success()
                : AlipayCommonResponse.fail("推送失败");
    }

    /**
     * 行程数据推送。
     */
    @PostMapping("/pushTransData")
    public AlipayCommonResponse pushTransData(@RequestBody AlipayTripPushTransDataReqDTO dto) {
        return alipayNotifyService.notifyPushTransData(null, dto)
                ? AlipayCommonResponse.success()
                : AlipayCommonResponse.fail("推送失败");
    }

    /**
     * 行业数据推送。
     */
    @PostMapping("/receiveCardDataFromItp")
    public AlipayCommonResponse receiveCardDataFromItp(@RequestBody AlipayTripReceiveCardDataReqDTO dto) {
        return alipayNotifyService.notifyCardData(null, dto)
                ? AlipayCommonResponse.success()
                : AlipayCommonResponse.fail("推送失败");
    }

    /**
     * 黑名单状态变更通知。
     */
    @PostMapping("/receiveBlackListFromItp")
    public AlipayCommonResponse receiveBlackListFromItp(@RequestBody AlipayTripReceiveBlackListReqDTO dto) {
        return alipayNotifyService.notifyBlackList(null, dto)
                ? AlipayCommonResponse.success()
                : AlipayCommonResponse.fail("推送失败");
    }
}