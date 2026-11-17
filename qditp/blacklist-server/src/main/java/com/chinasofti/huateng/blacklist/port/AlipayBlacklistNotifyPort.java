package com.chinasofti.huateng.blacklist.port;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


@Component
public class AlipayBlacklistNotifyPort {

    private static final Logger log = LoggerFactory.getLogger(AlipayBlacklistNotifyPort.class);

    private static final String SUCCESS = "0000";


    private static final String CHANNEL_UNREACHABLE = "9001";

    private static final DateTimeFormatter OPTION_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AlipayPaySignClient alipayPaySignClient;

    public AlipayBlacklistNotifyPort(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }


    public RpcOutcome notifyBlackListChange(String cardId, String thirdUserId, String cardType,
                                            String blackListType, String reason) {
        AlipayBlackListNotifyReqDTO request = new AlipayBlackListNotifyReqDTO();
        request.setCardId(cardId);
        request.setThirdUserId(thirdUserId);
        request.setCardType(cardType);
        request.setBlackListType(blackListType);
        request.setOptionDate(LocalDateTime.now().format(OPTION_DATE));
        request.setReason(reason);

        AlipayCommonResponse response;
        try {
            response = alipayPaySignClient.notifyBlackListChange(request);
        } catch (RuntimeException e) {
            log.error("通知支付宝渠道黑名单变更抛异常，按不可达处置、进补偿队列, cardId={}, blackListType={}",
                    cardId, blackListType, e);
            return new RpcOutcome.Unreachable(e);
        }

        if (response == null) {
            log.error("通知支付宝渠道黑名单变更拿到空响应，按不可达处置、进补偿队列, cardId={}, blackListType={}",
                    cardId, blackListType);
            return new RpcOutcome.Unreachable(new IllegalStateException("支付宝渠道黑名单通知返回空响应"));
        }
        if (SUCCESS.equals(response.getRetCode())) {
            log.info("通知支付宝渠道黑名单变更完成, cardId={}, blackListType={}", cardId, blackListType);
            return new RpcOutcome.Ok();
        }
        if (CHANNEL_UNREACHABLE.equals(response.getRetCode())) {
            log.error("通知支付宝渠道黑名单变更未拿到支付中心结论（retCode={}），按不可达处置、进补偿队列, cardId={}, blackListType={}, retMsg={}",
                    CHANNEL_UNREACHABLE, cardId, blackListType, response.getRetMsg());
            return new RpcOutcome.Unreachable(
                    new IllegalStateException("支付宝渠道未拿到支付中心结论: " + response.getRetMsg()));
        }
        log.error("通知支付宝渠道黑名单变更被业务拒绝，重推无意义、MUST 一次即终态并人工核对, cardId={}, blackListType={}, retCode={}, retMsg={}",
                cardId, blackListType, response.getRetCode(), response.getRetMsg());
        return new RpcOutcome.BizRejected(response.getRetCode(), response.getRetMsg());
    }
}
