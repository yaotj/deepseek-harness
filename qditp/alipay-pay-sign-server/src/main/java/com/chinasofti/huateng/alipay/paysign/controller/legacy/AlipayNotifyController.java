package com.chinasofti.huateng.alipay.paysign.controller.legacy;

import com.chinasofti.huateng.alipay.paysign.service.impl.notify.PaymentNotifyAdapter;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class AlipayNotifyController {

    private static final Logger log = LoggerFactory.getLogger(AlipayNotifyController.class);
    private final PaymentNotifyAdapter paymentNotifyAdapter;

    public AlipayNotifyController(PaymentNotifyAdapter paymentNotifyAdapter) {
        this.paymentNotifyAdapter = paymentNotifyAdapter;
    }


    @PostMapping("/channel/notify/blackListChange")
    public AlipayCommonResponse notifyBlackListChange(@RequestBody AlipayBlackListNotifyReqDTO request) {
        log.info("收到黑名单变更通知: cardId={}", request != null ? request.getCardId() : null);
        AlipayCommonResponse response = paymentNotifyAdapter.notifyBlackListChange(request);
        log.info("黑名单变更通知响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }


    @PostMapping("/channel/notify/closeResultForAlipay")
    public AlipayCommonResponse closeResultForAlipay(@RequestBody AlipayTripCloseResultReqDTO request) {
        String agreementNo = request != null ? request.getAgreementNo() : null;
        Boolean result = request != null ? request.getResult() : null;
        log.info("收到业务关闭结果通知: agreementNo={}, result={}", agreementNo, result);
        if (agreementNo == null || agreementNo.isEmpty() || result == null) {
            log.warn("业务关闭结果通知参数不完整: agreementNo={}, result={}", agreementNo, result);
            return AlipayCommonResponse.fail("协议号与关闭结果必填");
        }
        AlipayCommonResponse response = paymentNotifyAdapter.notifyCloseResult(agreementNo, result);
        log.info("业务关闭结果通知响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}
