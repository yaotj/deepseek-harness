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

    @Test
    void bizDataIsSerializedAsJsonString() {
        when(client.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        port("00", "").pushSignResult(signBizData());

        JSONObject bizData = JSON.parseObject(captureRequest().bizDataJson());
        assertEquals("U-TEST-0009", bizData.getString("thirdUserId"));
        assertEquals("0052290701523995", bizData.getString("requestSignSeq"));
        assertEquals("SUCCESS", bizData.getString("signResult"));
    }

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
