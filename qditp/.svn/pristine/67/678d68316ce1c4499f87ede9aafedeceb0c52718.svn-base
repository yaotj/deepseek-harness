package com.chinasofti.huateng.alipay.account.controller;

import com.chinasofti.huateng.alipay.account.service.AlipayAccountService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行-开卡申请接口（供 fep-alipay-server 调用）。
 */
@RestController
@RequestMapping("/channel")
public class FepAlipayTripRequestApplicationController {

    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripRequestApplicationController.class);

    @Autowired
    private AlipayAccountService alipayAccountService;

    /**
     * 开卡申请。
     */
    @PostMapping("/requestApplication")
    public AlipayTripRequestApplicationRespDTO requestApplication(@RequestBody AlipayTripRequestApplicationReqDTO request) {
        log.info("接收到开卡申请请求: {}", request);
        AlipayTripRequestApplicationRespDTO response = alipayAccountService.requestApplication(request);
        log.info("开卡申请响应: retCode={}, retMsg={}, cardId={}",
                response.getRetCode(), response.getRetMsg(), response.getCardId());
        return response;
    }

    /**
     * 查询支付宝用户信息。
     */
    @GetMapping("/queryUserInfo")
    public AlipayUserInfoDTO queryUserInfo(@RequestParam String thirdUserId) {
        log.info("接收到查询用户信息请求: thirdUserId={}", thirdUserId);
        AlipayUserInfoDTO userInfo = alipayAccountService.selectByThirdUserId(thirdUserId);
        if (userInfo == null) {
            log.warn("用户不存在, thirdUserId={}", thirdUserId);
        } else {
            log.info("查询用户信息成功, thirdUserId={}, cardId={}, cardType={}",
                    thirdUserId, userInfo.getCardId(), userInfo.getCardType());
        }
        return userInfo;
    }

    /**
     * 更新用户支付通道信息。
     */
    @GetMapping("/updatePaymentChannel")
    public Boolean updatePaymentChannel(@RequestParam String thirdUserId,
                                        @RequestParam String thirdPayId,
                                        @RequestParam String reqContractNo) {
        log.info("接收到更新支付通道请求: thirdUserId={}, thirdPayId={}, reqContractNo={}",
                thirdUserId, thirdPayId, reqContractNo);
        boolean result = alipayAccountService.updatePaymentChannel(thirdUserId, thirdPayId, reqContractNo);
        log.info("更新支付通道结果: thirdUserId={}, result={}", thirdUserId, result);
        return result;
    }
}
