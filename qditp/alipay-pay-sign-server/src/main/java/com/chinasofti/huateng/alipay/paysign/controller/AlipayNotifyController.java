package com.chinasofti.huateng.alipay.paysign.controller;

import com.chinasofti.huateng.alipay.paysign.service.AlipayTripPaymentService;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部约定的入向通知收口：黑名单状态变更通知、业务关闭结果通知。
 *
 * <p>两条都是本平台内部约定（分别由 blacklist-server 与 fep-alipay-server 推送），**不在**支付中心网关契约
 * §5 的回调清单内；契约 §5 那两条（支付结果 / 退款结果）在 {@code controller/paycenter/PayCenterCallbackController}
 * （2026-09-18 迁入 `controller.paycenter` 子包，URL 未动）。
 *
 * <p>类级不设 {@code @RequestMapping} 是历史形态：两条路径同属 {@code /channel/notify} 前缀，
 * 保留方法级完整路径以免动到已在用的对外 URL。</p>
 */
@RestController
public class AlipayNotifyController {

    private static final Logger log = LoggerFactory.getLogger(AlipayNotifyController.class);
    private final AlipayTripPaymentService alipayTripPaymentService;

    public AlipayNotifyController(AlipayTripPaymentService alipayTripPaymentService) {
        this.alipayTripPaymentService = alipayTripPaymentService;
    }

    /**
     * 支付宝出行-黑名单状态变更通知（blacklist-server 推送）。
     */
    @PostMapping("/channel/notify/blackListChange")
    public AlipayCommonResponse notifyBlackListChange(@RequestBody AlipayBlackListNotifyReqDTO request) {
        log.info("收到黑名单变更通知: cardId={}", request != null ? request.getCardId() : null);
        AlipayCommonResponse response = alipayTripPaymentService.notifyBlackListChange(request);
        log.info("黑名单变更通知响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }

    /**
     * 支付宝出行-业务关闭结果通知（fep-alipay-server 转发，收到后由本服务再通知支付中心）。
     *
     * <p>此前 {@code AlipayPaySignClient.notifyCloseResult} 一直在打这条路径，而本服务没有对应 handler，
     * 该链路实际是 404 被伪装成 HTTP 200 + UUID retCode。本端点即为补齐，路径与 Client 现有字面量逐字一致。
     */
    @PostMapping("/channel/notify/closeResultForAlipay")
    public AlipayCommonResponse closeResultForAlipay(@RequestBody AlipayTripCloseResultReqDTO request) {
        String agreementNo = request != null ? request.getAgreementNo() : null;
        Boolean result = request != null ? request.getResult() : null;
        log.info("收到业务关闭结果通知: agreementNo={}, result={}", agreementNo, result);
        if (agreementNo == null || agreementNo.isEmpty() || result == null) {
            log.warn("业务关闭结果通知参数不完整: agreementNo={}, result={}", agreementNo, result);
            return AlipayCommonResponse.fail("协议号与关闭结果必填");
        }
        AlipayCommonResponse response = alipayTripPaymentService.notifyCloseResult(agreementNo, result);
        log.info("业务关闭结果通知响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}
