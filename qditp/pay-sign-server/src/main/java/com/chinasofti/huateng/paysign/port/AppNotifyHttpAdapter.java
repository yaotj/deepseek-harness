package com.chinasofti.huateng.paysign.port;

import static com.chinasofti.huateng.paysign.support.AppNotifySigner.buildItpSign;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.AppSignResultNotifyReqDTO;
import com.chinasofti.huateng.model.app.AppTerminationResultNotifyReqDTO;
import com.chinasofti.huateng.model.app.ItpCommonRequest;
import com.chinasofti.huateng.paysign.client.AppNotificationClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class AppNotifyHttpAdapter implements AppNotifyPort {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AppNotificationClient appNotificationClient;

    private final String signResultUrl;

    private final String terminationResultUrl;

    private final String providerId;

    private final String charset;

    private final String format;

    private final String deviceId;

    private final String signType;

    private final String signKey;

    public AppNotifyHttpAdapter(
            AppNotificationClient appNotificationClient,
            @Value("${app.notify.sign-result-url:}") String signResultUrl,
            @Value("${app.notify.termination-result-url:}") String terminationResultUrl,
            @Value("${itp.providerId:06}") String providerId,
            @Value("${itp.charset:UTF-8}") String charset,
            @Value("${itp.format:json}") String format,
            @Value("${itp.deviceId:ITP-PAY-SIGN}") String deviceId,
            @Value("${itp.signType:null}") String signType,
            @Value("${itp.signKey:}") String signKey) {
        this.appNotificationClient = appNotificationClient;
        this.signResultUrl = signResultUrl;
        this.terminationResultUrl = terminationResultUrl;
        this.providerId = providerId;
        this.charset = charset;
        this.format = format;
        this.deviceId = deviceId;
        this.signType = signType;
        this.signKey = signKey;
    }

    @Override
    public NotifyDelivery pushSignResult(AppSignResultNotifyReqDTO bizData) {
        return push(signResultUrl, bizData);
    }

    @Override
    public NotifyDelivery pushTerminationResult(AppTerminationResultNotifyReqDTO bizData) {
        return push(terminationResultUrl, bizData);
    }

    private NotifyDelivery push(String url, Object bizData) {
        ItpCommonRequest<Object> request = new ItpCommonRequest<>();
        request.setProviderId(providerId);
        request.setCharset(charset);
        request.setFormat(format);
        request.setTimestamp(LocalDateTime.now().format(DATETIME_FORMATTER));
        request.setDeviceId(deviceId);
        request.setSignType(signType);
        request.setBizData(bizData);
        request.setSign(buildItpSign(request, signKey));

        AppNotificationClient.NotificationResult result = appNotificationClient.notify(url, toClientRequest(request));
        return new NotifyDelivery(result.success(), result.message());
    }

    private AppNotificationClient.NotificationRequest toClientRequest(ItpCommonRequest<?> request) {
        return new AppNotificationClient.NotificationRequest(
                request.getProviderId(), request.getCharset(), request.getFormat(), request.getTimestamp(),
                request.getDeviceId(), request.getSignType(), request.getSign(),
                JSON.toJSONString(request.getBizData()));
    }
}
