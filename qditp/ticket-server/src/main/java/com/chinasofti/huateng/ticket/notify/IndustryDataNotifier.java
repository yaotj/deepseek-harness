package com.chinasofti.huateng.ticket.notify;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.app.AppIndustryDataNotifyReqDTO;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.utils.SignChannelUtils;
import com.chinasofti.huateng.rpc.industry.IndustryDataClient;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 行业数据反向推码链路 —— 调 {@code industry-data-server} 生码，再推给 APP 网关。 */
@Component
class IndustryDataNotifier extends FormDataNotifyTemplate {

    private static final Logger log = LoggerFactory.getLogger(IndustryDataNotifier.class);
    /** APP 网关受理成功的 retCode。 */
    private static final String APP_GATEWAY_SUCCESS_RET_CODE = "0000";
    /** industry-data-server 生码成功的 retCode。 */
    private static final String INDUSTRY_BUILD_SUCCESS_RET_CODE = "0000";

    private final IndustryDataClient industryDataClient;

    @Value("${app.notify.industry-data-url:http://127.0.0.1:8080/ci/app/receiveCardDataFromItp}")
    private String industryDataNotifyUrl;

    @Value("${app.notify.industry-data-enabled:true}")
    private boolean industryDataNotifyEnabled;

    @Value("${app.notify.alipay-industry-data-url:https://dtcustomer.bestonepay.com/ngopenplatform/notify/receiveCardDataFromItp}")
    private String alipayIndustryDataNotifyUrl;

    IndustryDataNotifier(IndustryDataClient industryDataClient,
                         @Qualifier("appNotifyHttpClient") OkHttpClient httpClient,
                         NotifyFormRequestFactory formRequestFactory) {
        super(httpClient, formRequestFactory);
        this.industryDataClient = industryDataClient;
    }

    /** 生码 + 推送整条链路。 */
    void notifyVerifyResult(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, String gateCardType) {
        if (!industryDataNotifyEnabled) {
            log.info("APP行业数据推送已关闭, cardId={}, ticketTransSeq={}",
                    request.getCardId(), request.getTicketTransSeq());
            return;
        }
        log.info("异步推送 APP 行业数据开始, request={}, qrCodeStatus={}, gateCardType={}",
                request, qrCodeStatus, gateCardType);

        IndustryCardDataBuildReqDTO cardDataRequest = buildCardDataRequest(request, qrCodeStatus, gateCardType);
        log.info("调用 industry-data-server 生成卡数据, request={}", JSON.toJSONString(cardDataRequest));
        IndustryCardDataBuildRespDTO cardDataResp = industryDataClient.buildCardData(cardDataRequest);
        log.info("调用 industry-data-server 生成卡数据完成, response={}", JSON.toJSONString(cardDataResp));
        if (cardDataResp == null || !INDUSTRY_BUILD_SUCCESS_RET_CODE.equals(cardDataResp.getRetCode())) {
            log.warn("异步推送 APP 行业数据失败, 生成卡数据失败, cardId={}, retCode={}, retMsg={}",
                    request.getCardId(),
                    cardDataResp == null ? null : cardDataResp.getRetCode(),
                    cardDataResp == null ? null : cardDataResp.getRetMsg());
            return;
        }

        AppIndustryDataNotifyReqDTO notifyRequest = buildNotifyRequest(request, cardDataResp.getCardData());
        String notifyUrl = isAlipayTripChannel(request) ? alipayIndustryDataNotifyUrl : industryDataNotifyUrl;
        if (!doNotifyIndustryData(notifyRequest, notifyUrl, request.getDeviceId())) {
            log.warn("APP 行业数据推送未受理，本次不补偿, cardId={}, thirdUserId={}, ticketStatus={}, txnSeq={}",
                    request.getCardId(), request.getItpUserId(),
                    qrCodeStatus == null ? null : qrCodeStatus.getCodeStatus(),
                    qrCodeStatus == null ? null : qrCodeStatus.getTxnSeq());
        }
    }

    /** 组装生码入参，口径 */
    private IndustryCardDataBuildReqDTO buildCardDataRequest(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus,
                                                            String gateCardType) {
        IndustryCardDataBuildReqDTO cardDataRequest = new IndustryCardDataBuildReqDTO();
        cardDataRequest.setThirdUserId(request.getItpUserId());
        cardDataRequest.setCardId(request.getCardId());
        cardDataRequest.setCardType(StringUtils.hasText(gateCardType)
                ? gateCardType.trim() : request.getCardType());
        cardDataRequest.setTicketStatus(qrCodeStatus.getCodeStatus());
        cardDataRequest.setLastTxnStation(qrCodeStatus.getLastTxnStation());
        cardDataRequest.setLastTxnTime(qrCodeStatus.getLastTxnTime());
        cardDataRequest.setGateInStation(qrCodeStatus.getGateInStation());
        cardDataRequest.setGateInTime(qrCodeStatus.getGateInTime());
        cardDataRequest.setTxnSeq(qrCodeStatus.getTxnSeq());
        cardDataRequest.setIssueChannelCode(resolveIssueChannelCode(request));
        cardDataRequest.setSignChannelCode(resolveSignChannelCode(request));
        return cardDataRequest;
    }

    /** 解析码体签约渠道位。 */
    private String resolveSignChannelCode(NotifyVerifyResultReqDTO request) {
        String accountChannel = SignChannelUtils.resolve(request.getPaymentVendor());
        if (StringUtils.hasText(accountChannel)) {
            String gateChannel = SignChannelUtils.resolve(request.getSignChannelCode());
            if (!accountChannel.equals(gateChannel)) {
                log.info("码体签约渠道改用 account CHANNEL 下发在线码, cardId={}, gateSignChannelCode={}, accountChannel={}",
                        request.getCardId(), request.getSignChannelCode(), accountChannel);
            }
            return accountChannel;
        }
        log.warn("码体签约渠道缺少 account CHANNEL，回退闸机上送值, cardId={}, gateSignChannelCode={}",
                request.getCardId(), request.getSignChannelCode());
        return SignChannelUtils.resolve(request.getSignChannelCode());
    }

    /** 爱山东（044A）的发行渠道强制为 01。 */
    private String resolveIssueChannelCode(NotifyVerifyResultReqDTO request) {
        return CardTypeMapping.isAiShanDong(request.getCardType()) ? "01" : request.getIssueChannelCode();
    }

    /** 支付宝出行渠道（且非爱山东）时，行业数据推的是支付宝那个 URL、不是 APP 网关。 */
    private boolean isAlipayTripChannel(NotifyVerifyResultReqDTO request) {
        return request != null
                && IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode())
                && !CardTypeMapping.isAiShanDong(request.getCardType());
    }

    /** 组装推给 APP 的业务参数，字段以甲方规范表 55「行业数据推送请求参数信息」为准。 */
    private AppIndustryDataNotifyReqDTO buildNotifyRequest(NotifyVerifyResultReqDTO request, String cardData) {
        AppIndustryDataNotifyReqDTO notifyRequest = new AppIndustryDataNotifyReqDTO();
        notifyRequest.setThirdUserId(request.getItpUserId());
        notifyRequest.setCardId(request.getCardId());
        notifyRequest.setCardType(request.getCardType());
        notifyRequest.setCardData(cardData);
        notifyRequest.setCompanionFlag(request.getCompanionFlag());
        notifyRequest.setSignType("00");
        notifyRequest.setSign("");
        return notifyRequest;
    }

    /**
     * 推送码体给 APP 网关。
     *
     * @return true 仅当对方明确返回 {@code 0000}，判定口径见 {@link #isAccepted}
     */
    private boolean doNotifyIndustryData(AppIndustryDataNotifyReqDTO notifyRequest, String targetUrl, String deviceId) {
        return post(targetUrl, JSON.toJSONString(notifyRequest), deviceId, "行业数据推送");
    }

    /** 应答判定钩子 —— 本链路 */
    @Override
    protected boolean isAccepted(boolean httpSuccessful, String responseBody) {
        return httpSuccessful && APP_GATEWAY_SUCCESS_RET_CODE.equals(parseRetCode(responseBody));
    }

    /** 从应答报文中取 {@code retCode}。 */
    private String parseRetCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return JSON.parseObject(responseBody).getString("retCode");
        } catch (Exception e) {
            log.warn("行业数据推送应答无法解析为 JSON, response={}", responseBody);
            return null;
        }
    }
}
