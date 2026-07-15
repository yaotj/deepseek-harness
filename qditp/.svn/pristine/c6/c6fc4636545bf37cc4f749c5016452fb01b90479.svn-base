package com.chinasofti.huateng.fep.alipay.notify.service.impl;

import com.chinasofti.huateng.fep.alipay.notify.constant.NotifyTypeEnum;
import com.chinasofti.huateng.fep.alipay.notify.model.AlipayNotifyLog;
import com.chinasofti.huateng.fep.alipay.notify.service.AlipayNotifyService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPushTransDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveCardDataReqDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 支付宝通知推送服务实现。
 */
@Service
public class AlipayNotifyServiceImpl implements AlipayNotifyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlipayNotifyServiceImpl.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;

    @Value("${alipay.notify.close-result-url:}")
    private String closeResultUrl;

    @Value("${alipay.notify.push-trans-data-url:}")
    private String pushTransDataUrl;

    @Value("${alipay.notify.receive-card-data-url:}")
    private String receiveCardDataUrl;

    @Value("${alipay.notify.receive-blacklist-url:}")
    private String receiveBlacklistUrl;

    public AlipayNotifyServiceImpl() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public boolean notifyCloseResult(String url, AlipayTripCloseResultReqDTO dto) {
        return doNotify(NotifyTypeEnum.CLOSE_RESULT, url, closeResultUrl, dto);
    }

    @Override
    public boolean notifyPushTransData(String url, AlipayTripPushTransDataReqDTO dto) {
        return doNotify(NotifyTypeEnum.PUSH_TRANS_DATA, url, pushTransDataUrl, dto);
    }

    @Override
    public boolean notifyCardData(String url, AlipayTripReceiveCardDataReqDTO dto) {
        return doNotify(NotifyTypeEnum.RECEIVE_CARD_DATA, url, receiveCardDataUrl, dto);
    }

    @Override
    public boolean notifyBlackList(String url, AlipayTripReceiveBlackListReqDTO dto) {
        return doNotify(NotifyTypeEnum.RECEIVE_BLACKLIST, url, receiveBlacklistUrl, dto);
    }

    private boolean doNotify(NotifyTypeEnum notifyType, String url, String propertyUrl, Object body) {
        String targetUrl = resolve(url, propertyUrl);
        if (targetUrl == null) {
            LOGGER.warn("支付宝通知 URL 未配置: notifyType={}", notifyType.getCode());
            return false;
        }

        AlipayNotifyLog notifyLog = new AlipayNotifyLog();
        notifyLog.setNotifyType(notifyType.getCode());
        notifyLog.setNotifyUrl(targetUrl);

        String requestBody;
        try {
            requestBody = OBJECT_MAPPER.writeValueAsString(body);
            notifyLog.setRequestBody(requestBody);
        } catch (Exception e) {
            LOGGER.error("序列化支付宝通知请求报文失败: notifyType={}, targetUrl={}", notifyType.getCode(), targetUrl, e);
            notifyLog.setStatus("FAILED");
            notifyLog.setResultCode("9999");
            notifyLog.setResultMsg("序列化请求报文失败");
            // TODO: 持久化 notifyLog 到 ALIPAY_NOTIFY_LOG
            return false;
        }

        LOGGER.info("向支付宝推送通知: notifyType={}, targetUrl={}, requestBody={}", notifyType.getCode(), targetUrl, requestBody);

        RequestBody requestBodyObj = RequestBody.create(requestBody, JSON);
        Request request = new Request.Builder()
                .url(targetUrl)
                .post(requestBodyObj)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            LOGGER.info("支付宝通知响应: notifyType={}, targetUrl={}, status={}, responseBody={}",
                    notifyType.getCode(), targetUrl, response.code(), responseBody);

            notifyLog.setResponseBody(responseBody);
            notifyLog.setResultCode(String.valueOf(response.code()));
            notifyLog.setResultMsg(response.message());
            notifyLog.setStatus(response.isSuccessful() ? "SUCCESS" : "FAILED");
            // TODO: 持久化 notifyLog 到 ALIPAY_NOTIFY_LOG

            return response.isSuccessful();
        } catch (IOException e) {
            LOGGER.error("调用支付宝通知接口失败: notifyType={}, targetUrl={}, requestBody={}",
                    notifyType.getCode(), targetUrl, requestBody, e);
            notifyLog.setStatus("FAILED");
            notifyLog.setResultCode("9999");
            notifyLog.setResultMsg(e.getMessage());
            // TODO: 持久化 notifyLog 到 ALIPAY_NOTIFY_LOG
            return false;
        }
    }

    private static String resolve(String url, String propertyUrl) {
        if (url != null && !url.isBlank()) {
            return url;
        }
        if (propertyUrl != null && !propertyUrl.isBlank()) {
            return propertyUrl;
        }
        return null;
    }
}