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

/**
 * 护栏：三个 gateway adapter —— 出向 URL 选择、回调地址回退、应答判读三件事。
 *
 * <p>这三个类是 src/main 里唯一还持有 PaySignGateway 的地方，也就是「打哪个地址、
 * 把应答判成哪一支」的唯一决定者，此前却一条直接测试都没有：只被 PaySignFacadeFixture
 * 间接经过，而那些用例断言的是领域行为，URL 传错、回退顺序写反都照样通过。
 *
 * <p>两类断言各自防的东西不同：
 * <ul>
 *   <li><b>URL</b> —— 打错地址在测试环境往往仍返 200 或统一错误码，只在对账时才发现「这批单子没到对端」。
 *       支付中心那边路径带 /v1（ADR 记过一次全量扣款零成功的事故），所以「哪个方法用哪个配置项」MUST 钉死。</li>
 *   <li><b>回退顺序</b> —— 回调地址回退写反不会报错，表现是「支付/签约成功但我方永远收不到通知」，
 *       靠补偿扫表兜着、看起来只是慢。</li>
 * </ul>
 */
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

    // ---------------- ContractGatewayAdapter ----------------

    /** 签约四个动作各自打自己的地址，NEVER 共用一个。 */
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

    /** 回调地址回退第一级：报文带 notifyUrl 就用它。 */
    @Test
    void contractNotifyUrlPrefersRequestValue() {
        properties.setDefaultNotifyUrl("http://itp.test/configured");
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(success(null));

        adapter.requestContract(signRequest("http://caller.test/mine", "http://caller.test/return"), ALIPAY_VENDOR);

        assertEquals("http://caller.test/mine", capturedBizData().get("notifyUrl"));
    }

    /** 第二级：报文没带就用配置默认值。 */
    @Test
    void contractNotifyUrlFallsBackToConfiguredDefault() {
        properties.setDefaultNotifyUrl("http://itp.test/configured");
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(success(null));

        adapter.requestContract(signRequest(null, "http://caller.test/return"), ALIPAY_VENDOR);

        assertEquals("http://itp.test/configured", capturedBizData().get("notifyUrl"));
    }

    /**
     * 第三级：配置也没有就退到 returnUrl。
     *
     * <p>这一级是历史行为，看着像凑数，但**不能删**：returnUrl 是前端跳回地址、不是服务端回调地址，
     * 真走到这一级说明配置缺了。钉住它是为了让「有没有走到第三级」在测试里可见，
     * NEVER 因为「看起来不合理」就静默改成返回 null —— 那会让 bizData 少一个键、对端行为再变一次。
     */
    @Test
    void contractNotifyUrlLastResortIsReturnUrl() {
        properties.setDefaultNotifyUrl(null);
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(success(null));

        adapter.requestContract(signRequest(null, "http://caller.test/return"), ALIPAY_VENDOR);

        assertEquals("http://caller.test/return", capturedBizData().get("notifyUrl"));
    }

    /** 成功码 ⇒ Accepted，且 data 原样可取。 */
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

    /** 非成功码 ⇒ Rejected，且对外文案优先用对端 msg。 */
    @Test
    void nonSuccessCodeIsRejectedAndPrefersRemoteMessage() {
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(failure(600, "操作失败"));

        GatewayReply reply = adapter.requestDismissal(SEQ);

        GatewayReply.Rejected rejected = assertInstanceOf(GatewayReply.Rejected.class, reply);
        assertEquals("操作失败", rejected.messageOr("兜底文案"));
    }

    /** 对端完全没答（null）⇒ 仍是 Rejected，且退到调用方给的语义化默认文案。 */
    @Test
    void nullResponseIsRejectedAndUsesDefaultMessage() {
        ContractGatewayAdapter adapter = new ContractGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(null);

        GatewayReply reply = adapter.requestDismissal(SEQ);

        GatewayReply.Rejected rejected = assertInstanceOf(GatewayReply.Rejected.class, reply);
        assertNull(rejected.raw());
        assertEquals("兜底文案", rejected.messageOr("兜底文案"));
    }

    // ---------------- PaymentGatewayAdapter ----------------

    /** 扣款打 requestPayUrl，回调地址回退到支付专用配置（NEVER 用签约那个 default-notify-url）。 */
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

    /**
     * code=9999 且措辞命中「已支付成功」⇒ AlreadyPaid，**不是** Rejected。
     *
     * <p>这是三分支里最要紧的一条：判成失败会让上游重推同一笔，而对端已经扣过款了。
     */
    @Test
    void alreadyPaidIsItsOwnBranchAndCountsAsSuccessful() {
        PaymentGatewayAdapter adapter = new PaymentGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(failure(9999, "该订单已支付成功"));

        PaymentReply reply = adapter.requestPay(payRequest(null));

        assertInstanceOf(PaymentReply.AlreadyPaid.class, reply);
        assertTrue(reply.successful());
    }

    /** 普通失败码仍是 Rejected（与上一条同为 9999 之外的对照）。 */
    @Test
    void plainFailureIsRejected() {
        PaymentGatewayAdapter adapter = new PaymentGatewayAdapter(properties, gateway);
        when(client.request(anyString(), anyMap())).thenReturn(failure(600, "余额不足"));

        PaymentReply reply = adapter.requestPay(payRequest(null));

        PaymentReply.Rejected rejected = assertInstanceOf(PaymentReply.Rejected.class, reply);
        assertEquals("余额不足", rejected.messageOr("兜底文案"));
        assertTrue(!reply.successful());
    }

    /**
     * 未配置 pay-query-url ⇒ 直接 Rejected 且**一次都不出网**。
     *
     * <p>这一支的调用点是「拉黑前先查支付中心真实状态」，配置缺失时按不拉黑处理 ——
     * NEVER 改成「查不到就当失败去拉黑」，那会把配置漏项变成误拉黑用户。
     */
    @Test
    void queryPayStatusWithoutConfiguredUrlNeverCallsGateway() {
        properties.setPayQueryUrl(null);
        PaymentGatewayAdapter adapter = new PaymentGatewayAdapter(properties, gateway);

        GatewayReply reply = adapter.queryPayStatus(ORDER);

        assertInstanceOf(GatewayReply.Rejected.class, reply);
        assertNull(reply.raw());
        verify(client, never()).request(anyString(), anyMap());
    }

    // ---------------- RefundGatewayAdapter ----------------

    /** 退款与退款查询是两个地址，NEVER 复用。 */
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

    /** refundQueryConfigured 反映配置真实状态：补偿任务据它决定跳过本轮而不是打空地址。 */
    @Test
    void refundQueryConfiguredReflectsProperty() {
        RefundGatewayAdapter configured = new RefundGatewayAdapter(properties, gateway);
        assertTrue(configured.refundQueryConfigured());

        properties.setRefundQueryUrl(null);
        RefundGatewayAdapter missing = new RefundGatewayAdapter(properties, gateway);
        assertTrue(!missing.refundQueryConfigured());
    }

    // ---------------- fixtures ----------------

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
