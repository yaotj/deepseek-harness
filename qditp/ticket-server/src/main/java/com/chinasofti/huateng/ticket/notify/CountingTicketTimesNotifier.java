package com.chinasofti.huateng.ticket.notify;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.app.AppCountingTicketTimesNotifyReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


@Component
class CountingTicketTimesNotifier extends FormDataNotifyTemplate {

    private static final Logger log = LoggerFactory.getLogger(CountingTicketTimesNotifier.class);
    private static final String APP_GATEWAY_SUCCESS_RET_CODE = "0000";
    private static final String LINK_NAME = "多日票次数扣减通知";


    @Value("${app.notify.counting-ticket-times-url:}")
    private String countingTicketTimesUrl;

    @Value("${app.notify.counting-ticket-times-enabled:false}")
    private boolean countingTicketTimesNotifyEnabled;

    CountingTicketTimesNotifier(@Qualifier("appNotifyHttpClient") OkHttpClient httpClient,
                                NotifyFormRequestFactory formRequestFactory) {
        super(httpClient, formRequestFactory);
    }


    void notifyCountingTimes(NotifyVerifyResultReqDTO request, int times) {
        if (!countingTicketTimesNotifyEnabled) {
            log.info("{}已关闭, cardId={}, ticketTransSeq={}",
                    LINK_NAME, request.getCardId(), request.getTicketTransSeq());
            return;
        }
        if (!StringUtils.hasText(countingTicketTimesUrl)) {
            log.warn("{}地址未配置（app.notify.counting-ticket-times-url 为空），本次跳过, cardId={}",
                    LINK_NAME, request.getCardId());
            return;
        }
        AppCountingTicketTimesNotifyReqDTO notifyRequest = buildNotifyRequest(request, times);
        if (!post(countingTicketTimesUrl, JSON.toJSONString(notifyRequest),
                request.getDeviceId(), LINK_NAME)) {
            log.warn("{}未受理，本次不补偿, cardId={}, thirdUserId={}, transSeq={}, times={}",
                    LINK_NAME, notifyRequest.getCardId(), notifyRequest.getThirdUserId(),
                    notifyRequest.getTransSeq(), notifyRequest.getTimes());
        }
    }

    private AppCountingTicketTimesNotifyReqDTO buildNotifyRequest(NotifyVerifyResultReqDTO request, int times) {
        AppCountingTicketTimesNotifyReqDTO notifyRequest = new AppCountingTicketTimesNotifyReqDTO();
        notifyRequest.setThirdUserId(request.getItpUserId());
        notifyRequest.setCardId(request.getCardId());
        notifyRequest.setTransSeq(request.getTicketTransSeq());
        notifyRequest.setTimes(String.valueOf(times));
        if (!StringUtils.hasText(request.getTicketTransSeq())) {
            log.warn("{}缺 ticketTransSeq，已按原值推送，APP 侧可能对不上单, cardId={}",
                    LINK_NAME, request.getCardId());
        }
        return notifyRequest;
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
