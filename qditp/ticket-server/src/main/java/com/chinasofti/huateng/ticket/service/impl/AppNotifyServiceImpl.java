package com.chinasofti.huateng.ticket.service.impl;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.alipaytrip.AlipayPushTransDataReqDTO;
import com.chinasofti.huateng.model.app.AppIndustryDataNotifyReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.rpc.industry.IndustryDataClient;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.entity.StationInfo;
import com.chinasofti.huateng.ticket.mapper.StationInfoMapper;
import com.chinasofti.huateng.ticket.service.AppNotifyService;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * APP 通知服务默认实现。
 */
@Service
public class AppNotifyServiceImpl implements AppNotifyService {
    private static final Logger log = LoggerFactory.getLogger(AppNotifyServiceImpl.class);

    private final Executor appNotifyExecutor;
    private final IndustryDataClient industryDataClient;
    private final StationInfoMapper stationInfoMapper;

    @Value("${app.notify.industry-data-url:http://127.0.0.1:8080/ci/app/receiveCardDataFromItp}")
    private String industryDataNotifyUrl;

    @Value("${app.notify.industry-data-enabled:true}")
    private boolean industryDataNotifyEnabled;

    @Value("${app.notify.alipay-industry-data-url:https://dtcustomer.bestonepay.com/ngopenplatform/notify/receiveCardDataFromItp}")
    private String alipayIndustryDataNotifyUrl;

    @Value("${app.notify.alipay-push-trans-data-url:https://dtcustomer.bestonepay.com/ngopenplatform/notify/pushTransData}")
    private String alipayPushTransDataUrl;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public AppNotifyServiceImpl(@Qualifier("appNotifyExecutor") Executor appNotifyExecutor,
                                IndustryDataClient industryDataClient,
                                StationInfoMapper stationInfoMapper) {
        this.appNotifyExecutor = appNotifyExecutor;
        this.industryDataClient = industryDataClient;
        this.stationInfoMapper = stationInfoMapper;
    }

    @Override
    public void notifyVerifyResult(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus) {
        appNotifyExecutor.execute(() -> {
            try {
                if (!industryDataNotifyEnabled) {
                    log.info("APP行业数据推送已关闭, cardId={}, ticketTransSeq={}",
                            request.getCardId(), request.getTicketTransSeq());
                    return;
                }
                log.info("异步推送 APP 行业数据开始, request={}, qrCodeStatus={}",
                        JSON.toJSONString(request), JSON.toJSONString(qrCodeStatus));

                IndustryCardDataBuildReqDTO cardDataRequest = buildCardDataRequest(request, qrCodeStatus);
                log.info("调用 industry-data-server 生成卡数据, request={}", JSON.toJSONString(cardDataRequest));
                IndustryCardDataBuildRespDTO cardDataResp = industryDataClient.buildCardData(cardDataRequest);
                log.info("调用 industry-data-server 生成卡数据完成, response={}", JSON.toJSONString(cardDataResp));
                if (cardDataResp == null || !"0000".equals(cardDataResp.getRetCode())) {
                    log.warn("异步推送 APP 行业数据失败, 生成卡数据失败, cardId={}, retCode={}, retMsg={}",
                            request.getCardId(),
                            cardDataResp == null ? null : cardDataResp.getRetCode(),
                            cardDataResp == null ? null : cardDataResp.getRetMsg());
                    return;
                }

                AppIndustryDataNotifyReqDTO notifyRequest = buildNotifyRequest(request, cardDataResp.getCardData());
                String notifyUrl = "07".equals(request.getIssueChannelCode()) ? alipayIndustryDataNotifyUrl : industryDataNotifyUrl;
                doNotifyIndustryData(notifyRequest, notifyUrl);
            } catch (Exception e) {
                log.error("异步推送 APP 行业数据异常, request={}", JSON.toJSONString(request), e);
            }
        });
    }

    private IndustryCardDataBuildReqDTO buildCardDataRequest(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus) {
        IndustryCardDataBuildReqDTO cardDataRequest = new IndustryCardDataBuildReqDTO();
        cardDataRequest.setThirdUserId(convertHexUserIdToDecimal(request.getItpUserId()));
        cardDataRequest.setCardId(request.getCardId());
        cardDataRequest.setCardType(request.getCardType());
        cardDataRequest.setTicketStatus(qrCodeStatus.getCodeStatus());
        cardDataRequest.setLastTxnStation(qrCodeStatus.getLastTxnStation());
        cardDataRequest.setLastTxnTime(qrCodeStatus.getLastTxnTime());
        cardDataRequest.setGateInStation(qrCodeStatus.getGateInStation());
        cardDataRequest.setGateInTime(qrCodeStatus.getGateInTime());
        cardDataRequest.setTxnSeq(qrCodeStatus.getTxnSeq());
        cardDataRequest.setIssueChannelCode(request.getIssueChannelCode());
        cardDataRequest.setSignChannelCode(request.getSignChannelCode());
        return cardDataRequest;
    }

    private AppIndustryDataNotifyReqDTO buildNotifyRequest(NotifyVerifyResultReqDTO request, String cardData) {
        AppIndustryDataNotifyReqDTO notifyRequest = new AppIndustryDataNotifyReqDTO();
        notifyRequest.setThirdUserId(convertHexUserIdToDecimal(request.getItpUserId()));
        notifyRequest.setCardId(request.getCardId());
        notifyRequest.setCardType(request.getCardType());
        notifyRequest.setCardData(cardData);
        notifyRequest.setSignType("00");
        notifyRequest.setSign("");
        return notifyRequest;
    }

    private void doNotifyIndustryData(AppIndustryDataNotifyReqDTO notifyRequest, String targetUrl) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("bizData", JSON.toJSONString(notifyRequest))
                .build();

        Request httpRequest = new Request.Builder()
                .url(targetUrl)
                .post(requestBody)
                .build();

        log.info("调用行业数据推送, url={}, request={}", targetUrl, JSON.toJSONString(notifyRequest));
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() == null ? null : response.body().string();
            log.info("调用行业数据推送完成, httpCode={}, response={}", response.code(), responseBody);
        } catch (Exception e) {
            log.error("调用行业数据推送异常, request={}", JSON.toJSONString(notifyRequest), e);
        }
    }

    private String convertHexUserIdToDecimal(String itpUserId) {
        if (itpUserId == null || itpUserId.trim().length() == 0) {
            return itpUserId;
        }
        try {
            return new java.math.BigInteger(itpUserId.trim(), 16).toString(10);
        } catch (NumberFormatException e) {
            log.warn("itpUserId不是合法16进制字符串，按原值推送APP, itpUserId={}", itpUserId);
            return itpUserId;
        }
    }

    @Override
    public void pushAlipayTripData(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, QRCodeTxnDetail detail) {
        try {
            AlipayPushTransDataReqDTO pushRequest = buildAlipayTripPushRequest(request, qrCodeStatus, detail);
            String bizData = JSON.toJSONString(pushRequest);
            log.info("推送行程数据给支付宝, bizData={}", bizData);
            doNotifyAlipayTripData(bizData);
        } catch (Exception e) {
            log.error("推送行程数据给支付宝异常, request={}", JSON.toJSONString(request), e);
        }
    }

    private AlipayPushTransDataReqDTO buildAlipayTripPushRequest(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, QRCodeTxnDetail detail) {
        AlipayPushTransDataReqDTO pushRequest = new AlipayPushTransDataReqDTO();
        pushRequest.setLogicCard(request.getCardId());
        pushRequest.setTransType(request.getTrxType());
        pushRequest.setTransTime(convertHandleDateTime(request.getHandleDateTime()));
        pushRequest.setTransSeq(request.getTicketTransSeq());
        pushRequest.setTransStation(request.getHandleStationCode());
        pushRequest.setTransLine(resolveLineCode(request.getHandleStationCode()));
        pushRequest.setTirpNo(request.getTicketTransSeq());
        pushRequest.setThirdUserId(convertHexUserIdToDecimal(request.getItpUserId()));
        pushRequest.setCardId(request.getCardId());
        pushRequest.setCardType(request.getCardType());
        pushRequest.setSignType("00");
        pushRequest.setSign("");
        return pushRequest;
    }

    private String convertHandleDateTime(String handleDateTime) {
        if (handleDateTime == null || handleDateTime.length() < 14) {
            return handleDateTime;
        }
        // yyyyMMddHHmmss -> yyyy-MM-dd HH:mm:ss
        return handleDateTime.substring(0, 4) + "-" +
               handleDateTime.substring(4, 6) + "-" +
               handleDateTime.substring(6, 8) + " " +
               handleDateTime.substring(8, 10) + ":" +
               handleDateTime.substring(10, 12) + ":" +
               handleDateTime.substring(12, 14);
    }

    private String resolveLineCode(String stationCode) {
        if (!StringUtils.hasText(stationCode)) {
            return null;
        }
        try {
            StationInfo stationInfo = stationInfoMapper.selectByStationCode(stationCode);
            if (stationInfo != null && StringUtils.hasText(stationInfo.getLineCode())) {
                return stationInfo.getLineCode();
            }
        } catch (Exception e) {
            log.warn("查询车站线路代码失败, stationCode={}", stationCode, e);
        }
        // 兜底：线路代码未知时，用车站代码作为线路代码
        return stationCode;
    }

    private void doNotifyAlipayTripData(String bizData) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("bizData", bizData)
                .build();

        Request httpRequest = new Request.Builder()
                .url(alipayPushTransDataUrl)
                .post(requestBody)
                .build();

        log.info("调用支付宝行程数据推送, url={}, bizData={}", alipayPushTransDataUrl, bizData);
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            log.info("调用支付宝行程数据推送完成, httpCode={}, response={}", response.code(), responseBody);
        } catch (Exception e) {
            log.error("调用支付宝行程数据推送异常, bizData={}", bizData, e);
        }
    }
}
