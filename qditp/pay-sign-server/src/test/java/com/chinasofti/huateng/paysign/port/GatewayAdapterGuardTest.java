package com.chinasofti.huateng.paysign.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;


class GatewayAdapterGuardTest {

    private static final String PREFIX = "http://pay-gateway.test/api/v1/";
    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";
    private static final String ORDER = "GT20260916000000001";
    private static final String ALIPAY_VENDOR = "03";

    private final PayGatewayClient client = mock(PayGatewayClient.class);
    private final PaySignGateway gateway = new PaySignGateway(client);
    private final PaySignProperties properties = new PaySignProperties();

    GatewayAdapterGuardTest() {
        properties.setContractUrl(PREFIX + "contract");
        properties.setContractAdvisoryUrl(PREFIX + "creditQuery");
        properties.setContractResultUrl(PREFIX + "contract/queryResult");
        properties.setTerminationUrl(PREFIX + "contract/dismissal");
        properties.setRequestPayUrl(PREFIX + "pay");
        properties.setPayQueryUrl(PREFIX + "pay/query");
        properties.setRequestRefundUrl(PREFIX + "refund");
        properties.setRefundQueryUrl(PREFIX + "refund/refundQuery");
        when(client.isSuccess(any())).thenCallRealMethod();
    }


    @Test
    void eachContractOperationHitsItsOwnConfiguredUrl() {
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(success(null));

        adapter.requestContract(signRequest("http://itp.test/notify", null), ALIPAY_VENDOR);
        adapter.creditQuery(USER, SEQ, ALIPAY_VENDOR);
        adapter.queryContractResult(SEQ);
        adapter.requestDismissal(SEQ);

        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(client, org.mockito.Mockito.times(4)).request(urls.capture(), anyMap());
        assertEquals(PREFIX + "contract", urls.getAllValues().get(0));
        assertEquals(PREFIX + "creditQuery", urls.getAllValues().get(1));
        assertEquals(PREFIX + "contract/queryResult", urls.getAllValues().get(2));
        assertEquals(PREFIX + "contract/dismissal", urls.getAllValues().get(3));
    }

    @Test
    void contractNotifyUrlPrefersRequestValue() {
        properties.setDefaultNotifyUrl("http://itp.test/configured");
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(success(null));

        adapter.requestContract(signRequest("http://caller.test/mine", "http://caller.test/return"), ALIPAY_VENDOR);

        assertEquals("http://caller.test/mine", capturedBizData().get("notifyUrl"));
    }

    @Test
    void contractNotifyUrlFallsBackToConfiguredDefault() {
        properties.setDefaultNotifyUrl("http://itp.test/configured");
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(success(null));

        adapter.requestContract(signRequest(null, "http://caller.test/return"), ALIPAY_VENDOR);

        assertEquals("http://itp.test/configured", capturedBizData().get("notifyUrl"));
    }


    @Test
    void contractNotifyUrlLastResortIsReturnUrl() {
        properties.setDefaultNotifyUrl(null);
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(success(null));

        adapter.requestContract(signRequest(null, "http://caller.test/return"), ALIPAY_VENDOR);

        assertEquals("http://caller.test/return", capturedBizData().get("notifyUrl"));
    }

    @Test
    void successCodeIsAcceptedAndKeepsData() {
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "SIGNED");
        when(client.request(anyString(), anyMap())).thenReturn(success(data));

        GatewayReply reply = adapter.queryContractResult(SEQ);

        GatewayReply.Accepted accepted = assertInstanceOf(GatewayReply.Accepted.class, reply);
        assertEquals("SIGNED", accepted.data().get("status"));
    }

    @Test
    void nonSuccessCodeIsRejectedAndPrefersRemoteMessage() {
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(failure(600, "操作失败"));

        GatewayReply reply = adapter.requestDismissal(SEQ);

        GatewayReply.Rejected rejected = assertInstanceOf(GatewayReply.Rejected.class, reply);
        assertEquals("操作失败", rejected.messageOr("兜底文案"));
    }

    @Test
    void nullResponseIsRejectedAndUsesDefaultMessage() {
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(null);

        GatewayReply reply = adapter.requestDismissal(SEQ);

        GatewayReply.Rejected rejected = assertInstanceOf(GatewayReply.Rejected.class, reply);
        assertNull(rejected.raw());
        assertEquals("兜底文案", rejected.messageOr("兜底文案"));
    }


    @Test
    void payUsesRequestPayUrlAndPaymentSpecificNotifyUrl() {
        properties.setDefaultNotifyUrl("http://itp.test/signNotify");
        properties.setRequestPayNotifyUrl("http://itp.test/payNotify");
        PaymentGatewayAdapter adapter = new PaymentGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(success(null));

        adapter.requestPay(payRequest(null));

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> bizData = captor();
        verify(client).request(url.capture(), bizData.capture());
        assertEquals(PREFIX + "pay", url.getValue());
        assertEquals("http://itp.test/payNotify", bizData.getValue().get("notifyUrl"));
    }


    @Test
    void alreadyPaidIsItsOwnBranchAndCountsAsSuccessful() {
        PaymentGatewayAdapter adapter = new PaymentGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(failure(9999, "该订单已支付成功"));

        PaymentReply reply = adapter.requestPay(payRequest(null));

        assertInstanceOf(PaymentReply.AlreadyPaid.class, reply);
        assertTrue(reply.successful());
    }

    @Test
    void plainFailureIsRejected() {
        PaymentGatewayAdapter adapter = new PaymentGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(failure(600, "余额不足"));

        PaymentReply reply = adapter.requestPay(payRequest(null));

        PaymentReply.Rejected rejected = assertInstanceOf(PaymentReply.Rejected.class, reply);
        assertEquals("余额不足", rejected.messageOr("兜底文案"));
        assertTrue(!reply.successful());
    }


    @Test
    void queryPayStatusWithoutConfiguredUrlNeverCallsGateway() {
        properties.setPayQueryUrl(null);
        PaymentGatewayAdapter adapter = new PaymentGatewayAdapter(properties, gateway);

        GatewayReply reply = adapter.queryPayStatus(ORDER);

        assertInstanceOf(GatewayReply.Rejected.class, reply);
        assertNull(reply.raw());
        verify(client, never()).request(anyString(), anyMap());
    }


    @Test
    void refundAndRefundQueryUseTheirOwnUrls() {
        RefundGatewayAdapter adapter = new RefundGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(success(null));

        adapter.requestRefund(new LinkedHashMap<>());
        adapter.queryRefund(new LinkedHashMap<>());

        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(client, org.mockito.Mockito.times(2)).request(urls.capture(), anyMap());
        assertEquals(PREFIX + "refund", urls.getAllValues().get(0));
        assertEquals(PREFIX + "refund/refundQuery", urls.getAllValues().get(1));
    }

    @Test
    void refundQueryConfiguredReflectsProperty() {
        RefundGatewayAdapter configured = new RefundGatewayAdapter(properties, gateway);
        assertTrue(configured.refundQueryConfigured());

        properties.setRefundQueryUrl(null);
        RefundGatewayAdapter missing = new RefundGatewayAdapter(properties, gateway);
        assertTrue(!missing.refundQueryConfigured());
    }


    private Map<String, Object> capturedBizData() {
        ArgumentCaptor<Map<String, Object>> bizData = captor();
        verify(client).request(anyString(), bizData.capture());
        return bizData.getValue();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captor() {
        return ArgumentCaptor.forClass(Map.class);
    }

    private RequestSignInfoReqDTO signRequest(String notifyUrl, String returnUrl) {
        RequestSignInfoReqDTO request = new RequestSignInfoReqDTO();
        request.setRequestSignSeq(SEQ);
        request.setThirdUserId(USER);
        request.setDisplayAccount("138****8000");
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);
        return request;
    }

    private RequestPayReqDTO payRequest(String notifyUrl) {
        RequestPayReqDTO request = new RequestPayReqDTO();
        request.setOrderNo(ORDER);
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setRequestSignSeq(SEQ);
        request.setAmount(2);
        request.setNotifyUrl(notifyUrl);
        return request;
    }

    private PaySignGatewayResponse success(Map<String, Object> data) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(0);
        response.setMsg("成功");
        response.setData(data);
        return response;
    }

    private PaySignGatewayResponse failure(int code, String msg) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }
}
