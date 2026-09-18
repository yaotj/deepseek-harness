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

/**
 * {@link AppNotifyPort} 的唯一实现：APP 出向通知的地址、报文骨架、时间戳与加签都只在这里出现一次。
 *
 * <p>把这三样从通知服务里搬进来的理由是它们与「通知状态机 + 落库重试」
 * 是**不相交的依赖簇**（ADR-D95）：前者只依赖配置与 HTTP 客户端，后者只依赖 mapper 与线程池。
 * 当时的宿主 {@code AppNotifyServiceImpl} 已于 ADR-D127 按聚合拆成
 * {@code SignNotifyServiceImpl} + {@code TerminationNotifyServiceImpl}，本类不受影响。
 *
 * <p>{@code itp.signKey} 只在本类内传给 {@code buildItpSign}，**NEVER 打进日志**。
 */
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

    /** 协作者与配置一律构造注入（ADR-D96）：对象一建成即完备，测试里也能逐项指定。 */
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

    /** 把领域通知模型转换为客户端只关心的 ITP 表单参数。 */
    private AppNotificationClient.NotificationRequest toClientRequest(ItpCommonRequest<?> request) {
        return new AppNotificationClient.NotificationRequest(
                request.getProviderId(), request.getCharset(), request.getFormat(), request.getTimestamp(),
                request.getDeviceId(), request.getSignType(), request.getSign(),
                JSON.toJSONString(request.getBizData()));
    }
}
