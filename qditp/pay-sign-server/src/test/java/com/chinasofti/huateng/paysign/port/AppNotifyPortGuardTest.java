package com.chinasofti.huateng.paysign.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.AppSignResultNotifyReqDTO;
import com.chinasofti.huateng.model.app.AppTerminationResultNotifyReqDTO;
import com.chinasofti.huateng.paysign.client.AppNotificationClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 护栏：APP 出向通知的**地址选择**与**报文骨架装配**只在 adapter 里发生一次。
 *
 * <p>此前这两件事散在 {@code AppNotifyServiceImpl} 的三个 {@code doNotify*} 里，
 * 「签约结果推到了解约地址」这类错在单测里完全看不出来。
 */
class AppNotifyPortGuardTest {

    private static final String SIGN_URL = "http://app.example/ci/app/receiveSignResult";
    private static final String TERMINATION_URL = "http://app.example/ci/app/receiveTerminationResultFromItp";

    private final AppNotificationClient client = mock(AppNotificationClient.class);

    private AppNotifyPort port(String signType, String signKey) {
        return new AppNotifyHttpAdapter(client, SIGN_URL, TERMINATION_URL,
                "06", "UTF-8", "json", "ITP-PAY-SIGN", signType, signKey);
    }

    private AppNotificationClient.NotificationRequest captureRequest() {
        ArgumentCaptor<AppNotificationClient.NotificationRequest> captor =
                ArgumentCaptor.forClass(AppNotificationClient.NotificationRequest.class);
        verify(client).notify(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void signResultGoesToSignUrlNeverToTerminationUrl() {
        when(client.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        port("00", "").pushSignResult(signBizData());

        verify(client).notify(eq(SIGN_URL), any());
    }

    @Test
    void terminationResultGoesToTerminationUrl() {
        when(client.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        port("00", "").pushTerminationResult(terminationBizData());

        verify(client).notify(eq(TERMINATION_URL), any());
    }

    /** 报文骨架六个字段全部来自配置，NEVER 由调用方传入。 */
    @Test
    void envelopeFieldsComeFromConfiguration() {
        when(client.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        port("00", "").pushSignResult(signBizData());

        AppNotificationClient.NotificationRequest request = captureRequest();
        assertEquals("06", request.providerId());
        assertEquals("UTF-8", request.charset());
        assertEquals("json", request.format());
        assertEquals("ITP-PAY-SIGN", request.deviceId());
        assertEquals("00", request.signType());
        assertEquals(14, request.timestamp().length(), "timestamp MUST 是 yyyyMMddHHmmss");
    }

    /** bizData 以 JSON 字符串出网，字段名即对外契约。 */
    @Test
    void bizDataIsSerializedAsJsonString() {
        when(client.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        port("00", "").pushSignResult(signBizData());

        JSONObject bizData = JSON.parseObject(captureRequest().bizDataJson());
        assertEquals("U-TEST-0009", bizData.getString("thirdUserId"));
        assertEquals("0052290701523995", bizData.getString("requestSignSeq"));
        assertEquals("SUCCESS", bizData.getString("signResult"));
    }

    /** {@code signType=00} 是免签，sign MUST 为空；这条与下一条一起钉住 signKey 的唯一使用点。 */
    @Test
    void signTypeZeroZeroMeansNoSignature() {
        when(client.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        port("00", "SECRET").pushSignResult(signBizData());

        assertNull(captureRequest().sign());
    }

    @Test
    void signTypeZeroOneProducesSignature() {
        when(client.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        port("01", "SECRET").pushSignResult(signBizData());

        assertNotNull(captureRequest().sign());
    }

    @Test
    void deliveredResultIsPassedThrough() {
        when(client.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        NotifyDelivery delivery = port("00", "").pushSignResult(signBizData());

        assertTrue(delivery.delivered());
        assertEquals("通知成功", delivery.message());
    }

    /** 失败原因 MUST 原样带回，调用方要拿它落 NOTIFY_RESULT。 */
    @Test
    void failureMessageIsPassedThrough() {
        when(client.notify(any(), any()))
                .thenReturn(AppNotificationClient.NotificationResult.failure("业务失败:9999:签约不存在"));

        NotifyDelivery delivery = port("00", "").pushTerminationResult(terminationBizData());

        assertFalse(delivery.delivered());
        assertEquals("业务失败:9999:签约不存在", delivery.message());
    }

    private AppSignResultNotifyReqDTO signBizData() {
        AppSignResultNotifyReqDTO bizData = new AppSignResultNotifyReqDTO();
        bizData.setThirdUserId("U-TEST-0009");
        bizData.setRequestSignSeq("0052290701523995");
        bizData.setPaymentVendor("03");
        bizData.setSignResult("SUCCESS");
        return bizData;
    }

    private AppTerminationResultNotifyReqDTO terminationBizData() {
        AppTerminationResultNotifyReqDTO bizData = new AppTerminationResultNotifyReqDTO();
        bizData.setThirdUserId("U-TEST-0009");
        bizData.setRequestSignSeq("0052290701523995");
        bizData.setTerminationResult("SUCCESS");
        return bizData;
    }
}
