package com.chinasofti.huateng.ticket.notify;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.app.AppCardTranNotifyReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.utils.SignChannelUtils;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * R6「卡片行程通知」/app/receiveCardTran：过闸、补站成功后由 ITP 推给 APP。
 * 与 §3.63 同形态：空地址默认 + 默认关闭，失败只 WARN 不补偿。
 */
@Component
class CardTranNotifier extends FormDataNotifyTemplate {

    private static final Logger log = LoggerFactory.getLogger(CardTranNotifier.class);
    private static final String APP_GATEWAY_SUCCESS_RET_CODE = "0000";
    private static final String LINK_NAME = "卡片行程通知";
    private static final String CHANNEL_TYPE_GATE = "00";

    @Value("${app.notify.card-tran-url:}")
    private String cardTranUrl;

    @Value("${app.notify.card-tran-enabled:false}")
    private boolean cardTranNotifyEnabled;

    CardTranNotifier(@Qualifier("appNotifyHttpClient") OkHttpClient httpClient,
                     NotifyFormRequestFactory formRequestFactory) {
        super(httpClient, formRequestFactory);
    }

    void notifyCardTran(NotifyVerifyResultReqDTO request, QRCodeStatus effectiveStatus, String countingFlag) {
        if (!cardTranNotifyEnabled) {
            log.info("{}已关闭, cardId={}, ticketTransSeq={}",
                    LINK_NAME, request.getCardId(), request.getTicketTransSeq());
            return;
        }
        if (!StringUtils.hasText(cardTranUrl)) {
            log.warn("{}地址未配置（app.notify.card-tran-url 为空），本次跳过, cardId={}",
                    LINK_NAME, request.getCardId());
            return;
        }
        AppCardTranNotifyReqDTO notifyRequest = buildNotifyRequest(request, effectiveStatus, countingFlag);
        if (!post(cardTranUrl, JSON.toJSONString(notifyRequest), request.getDeviceId(), LINK_NAME)) {
            log.warn("{}未受理，本次不补偿, cardId={}, thirdUserId={}, ticketStatus={}, tikcetTransSeq={}",
                    LINK_NAME, notifyRequest.getCardId(), notifyRequest.getThirdUserId(),
                    notifyRequest.getTicketStatus(), notifyRequest.getTikcetTransSeq());
        }
    }

    AppCardTranNotifyReqDTO buildNotifyRequest(NotifyVerifyResultReqDTO request,
                                               QRCodeStatus effectiveStatus,
                                               String countingFlag) {
        AppCardTranNotifyReqDTO notifyRequest = new AppCardTranNotifyReqDTO();
        notifyRequest.setThirdUserId(request.getItpUserId());
        notifyRequest.setTrxType(request.getTrxType());
        notifyRequest.setTicketStatus(effectiveStatus == null ? null : effectiveStatus.getCodeStatus());
        notifyRequest.setSignChannelCode(resolveSignChannelCode(request));
        notifyRequest.setCardId(request.getCardId());
        notifyRequest.setCardType(request.getCardType());
        notifyRequest.setHandleDateTime(request.getHandleDateTime());
        notifyRequest.setHandleStationCode(request.getHandleStationCode());
        notifyRequest.setTrxAmount(request.getTrxAmount());
        notifyRequest.setOvertimeAmount(request.getOvertimeAmount());
        notifyRequest.setLastTicketStatus(request.getLastTicketStatus());
        notifyRequest.setLastHandleStationCode(request.getLastHandleStationCode());
        notifyRequest.setLastHandleDateTime(request.getLastHandleDateTime());
        notifyRequest.setTikcetTransSeq(request.getTicketTransSeq());
        notifyRequest.setDeviceId(request.getDeviceId());
        notifyRequest.setChannelType(resolveChannelType(request));
        notifyRequest.setCompanionFlag(request.getCompanionFlag());
        notifyRequest.setCountingFlag(countingFlag);
        return notifyRequest;
    }

    private String resolveChannelType(NotifyVerifyResultReqDTO request) {
        String channelType = request.getChannelType();
        if (StringUtils.hasText(channelType)) {
            return channelType;
        }
        log.warn("{}缺 channelType，按闸机 00 兜底, cardId={}, ticketTransSeq={}",
                LINK_NAME, request.getCardId(), request.getTicketTransSeq());
        return CHANNEL_TYPE_GATE;
    }

    private String resolveSignChannelCode(NotifyVerifyResultReqDTO request) {
        String accountChannel = SignChannelUtils.resolve(request.getPaymentVendor());
        if (StringUtils.hasText(accountChannel)) {
            return accountChannel;
        }
        String gateChannel = SignChannelUtils.resolve(request.getSignChannelCode());
        if (!StringUtils.hasText(gateChannel)) {
            log.warn("{}缺少支付渠道编码，按原值推送, cardId={}, gateSignChannelCode={}",
                    LINK_NAME, request.getCardId(), request.getSignChannelCode());
        }
        return gateChannel;
    }

    @Override
    protected boolean isAccepted(boolean httpSuccessful, String responseBody) {
        return httpSuccessful && APP_GATEWAY_SUCCESS_RET_CODE.equals(parseRetCode(responseBody));
    }

    private String parseRetCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return JSON.parseObject(responseBody).getString("retCode");
        } catch (Exception e) {
            log.warn("{}应答无法解析为 JSON, response={}", LINK_NAME, responseBody);
            return null;
        }
    }
}
