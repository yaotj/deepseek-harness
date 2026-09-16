package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code PaySignWorkflow} 签约 / 咨询 / 解约申请三条入口的**特征测试**（批次 0 护栏）。
 *
 * <p><b>「特征测试」的含义</b>：这里断言的是**当前行为**，不是「应该怎样」。它的用途只有一个 ——
 * 拆分 `PaySignWorkflow` 时，凡是本类里的断言变红，就说明这次搬迁**改了对外可见行为**，
 * 不是重构而是改需求。因此 <b>NEVER 为了让某次改动通过而放宽这里的断言</b>；真要改行为，
 * MUST 先在 `docs/domain/decisions.md` 立 ADR 说明理由，再连同断言一起改。
 *
 * <p><b>三条被钉住的关键口径</b>：
 * <ul>
 *   <li>钱包（{@code payChannelCode=0B}）在传统签约链路上**一律短路、且绝不出网**——
 *       钱包没有签约协议，误调支付中心会在对端造出一条无主协议；</li>
 *   <li>{@code APP_PAY_SIGN_REQUEST.OPERATION_TYPE} 只落 {@code SIGN} / {@code UNSIGN} 两个值
 *       （13 个内部细分名由 {@code convertOperationType} 归并）。批次 1 抽 {@code writeLog} 时
 *       <b>这条 MUST 保持不变</b>，否则运营查流水的口径静默变了；</li>
 *   <li>解约申请落库必须是 {@code TERMINATION_STATUS=PENDING} + {@code NOTIFY_STATUS=PENDING}
 *       + {@code NOTIFY_RETRY_COUNT=0}，且**不调支付平台**（T+4 才确认，见
 *       {@code docs/business/pay-sign.md} §解约申请满 4 天才确认）。</li>
 * </ul>
 */
class ContractDomainCharacterizationTest {

    private static final String WALLET_VENDOR = "0B";
    private static final String ALIPAY_VENDOR = "03";
    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";

    /**
     * 钱包（{@code 0B}）的 {@code requestSignInfo} <b>必须能走到支付中心</b>并拿回 SDK 参数。
     *
     * <p>本用例此前叫 {@code walletRequestSignInfoIsRejectedAndNeverReachesGateway}，断言 8001 +
     * 永不到网关。那条契约已于 2026-09-15 作废：支付中心 §1.1 requestPay 的 withholding 场景
     * 强制要求 {@code requestSignSeq}（实测 {@code code=9999「代扣签约请求流水号不能为空」}），
     * 钱包不在支付中心签约就永远扣不出去（`PAY_TXN_DETAIL` 里 0B 渠道零条 SUCCESS）。
     * 详细论证见 {@code ContractDomainServiceImpl.requestSignInfo} 方法体内注释。<b>NEVER 回退。</b>
     */
    @Test
    void walletRequestSignInfoReachesGatewayLikeOtherVendors() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignGatewayResponse gateway = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", "wallet://contract?fake=1");
        gateway.setData(data);
        fixture.gatewayReturns(gateway);

        RequestSignInfoResult result = fixture.service.requestSignInfo(signInfoRequest(WALLET_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("wallet://contract?fake=1", result.getRequestStartSdkInfo());
        verify(fixture.payGatewayClient).request(anyString(), anyMap());
    }

    @Test
    void requestSignInfoRejectsAlreadySignedUser() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_VENDOR)).thenReturn(new PaySignInfo());

        RequestSignInfoResult result = fixture.service.requestSignInfo(signInfoRequest(ALIPAY_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.ALREADY_SIGNED.getCode(), result.getRetCode());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /** 网关成功且 {@code data.data} 有值时，原样作为 SDK 参数回给 APP。 */
    @Test
    void requestSignInfoReturnsSdkInfoFromGatewayData() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignGatewayResponse gateway = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", "alipays://platformapi/startapp?fake=1");
        gateway.setData(data);
        fixture.gatewayReturns(gateway);

        RequestSignInfoResult result = fixture.service.requestSignInfo(signInfoRequest(ALIPAY_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("alipays://platformapi/startapp?fake=1", result.getRequestStartSdkInfo());
    }

    /** 网关未成功时**不返 SDK 参数**，统一收成 9001，NEVER 退化成「成功但参数为空」。 */
    @Test
    void requestSignInfoMapsGatewayFailureToSystemError() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        RequestSignInfoResult result = fixture.service.requestSignInfo(signInfoRequest(ALIPAY_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
    }

    /** 签约侧流水的 {@code OPERATION_TYPE} 恒为 {@code SIGN}（批次 1 抽 writeLog 时 MUST 不变）。 */
    @Test
    void signSideAuditLogOperationTypeIsAlwaysSign() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        fixture.service.requestSignInfo(signInfoRequest(ALIPAY_VENDOR), "01");

        List<PaySignRequest> logs = fixture.auditLogs();
        assertTrue(logs.size() >= 1, "requestSignInfo MUST 落审计流水");
        assertEquals("SIGN", logs.get(logs.size() - 1).getOperationType());
    }

    @Test
    void walletContractAdvisoryIsAnsweredSuccessfullyWithoutGateway() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        RequestContractAdvisoryRespDTO result = fixture.service.requestContractAdvisory(advisoryRequest(WALLET_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /**
     * 咨询打的是 {@code pay.sign.contract-advisory-url}。
     *
     * <p>钉住「哪个入口用哪条 URL」：批次 3 要把网关报文组装抽成独立适配层，
     * URL 错配在生产上表现为 {@code code=600 操作失败}（不是 404），极难定位。
     */
    @Test
    void contractAdvisoryUsesConfiguredAdvisoryUrl() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());

        fixture.service.requestContractAdvisory(advisoryRequest(ALIPAY_VENDOR), "01");

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(fixture.payGatewayClient).request(url.capture(), anyMap());
        assertEquals(PaySignFacadeFixture.GATEWAY_URL_PREFIX + "creditQuery", url.getValue());
    }

    @Test
    void contractAdvisoryMapsGatewayFailureToSystemError() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        RequestContractAdvisoryRespDTO result = fixture.service.requestContractAdvisory(advisoryRequest(ALIPAY_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
    }

    /** 无签约记录时回 8011，且解约侧流水的 {@code OPERATION_TYPE} 归并为 {@code UNSIGN}。 */
    @Test
    void requestTerminationRejectsUnsignedUserAndLogsUnsignOperation() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, ALIPAY_VENDOR)).thenReturn(null);

        RequestTerminationRespDTO result = fixture.service.requestTermination(terminationRequest(), "01");

        assertEquals(PaySignErrorCodeEnum.USER_NOT_SIGNED.getCode(), result.getRetCode());
        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals("UNSIGN", logs.get(logs.size() - 1).getOperationType());
    }

    /**
     * 首次申请落 {@code PENDING} 三件套，且**不碰支付平台**。
     *
     * <p>「不碰支付平台」是业务口径（T+4 才确认）而非性能优化：在这里提前解约，
     * 用户在满 4 天前的欠费就再也扣不到了。
     */
    @Test
    void requestTerminationInsertsPendingRequestAndSkipsPayPlatform() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setCardId("CARD-1");
        signInfo.setCardType("0441");
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, ALIPAY_VENDOR)).thenReturn(signInfo);

        RequestTerminationRespDTO result = fixture.service.requestTermination(terminationRequest(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals(SEQ, result.getRequestSignSeq());
        ArgumentCaptor<AppTerminationRequest> captor = ArgumentCaptor.forClass(AppTerminationRequest.class);
        verify(fixture.terminationRequestMapper).insert(captor.capture());
        AppTerminationRequest inserted = captor.getValue();
        assertEquals("PENDING", inserted.getTerminationStatus());
        assertEquals("PENDING", inserted.getNotifyStatus());
        assertEquals(Integer.valueOf(0), inserted.getNotifyRetryCount());
        assertNotNull(inserted.getRequestTime());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /**
     * 同一流水已有**非 FAILED** 解约申请时拒绝（8009）。
     *
     * <p>白名单只放 {@code FAILED} 复活，其余状态一律拒 —— 无条件 insert 会撞
     * {@code UK_ATR_REQUEST_SIGN_SEQ} 并被外层 catch 成 9001，那条流水从此永久无法再申请解约。
     */
    @Test
    void requestTerminationRejectsWhenSeqAlreadyHasNonFailedRequest() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, ALIPAY_VENDOR)).thenReturn(new PaySignInfo());
        AppTerminationRequest existing = new AppTerminationRequest();
        existing.setTerminationStatus("SUCCESS");
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(existing);

        RequestTerminationRespDTO result = fixture.service.requestTermination(terminationRequest(), "01");

        assertEquals(PaySignErrorCodeEnum.ALREADY_TERMINATING.getCode(), result.getRetCode());
        verify(fixture.terminationRequestMapper, never()).insert(any());
    }

    private RequestSignInfoReqDTO signInfoRequest(String payChannelCode) {
        RequestSignInfoReqDTO request = new RequestSignInfoReqDTO();
        request.setThirdUserId(USER);
        request.setDisplayAccount("138****8000");
        request.setPayChannelCode(payChannelCode);
        request.setRequestSignSeq(SEQ);
        return request;
    }

    private RequestContractAdvisoryReqDTO advisoryRequest(String paymentVendor) {
        RequestContractAdvisoryReqDTO request = new RequestContractAdvisoryReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(paymentVendor);
        return request;
    }

    private RequestTerminationReqDTO terminationRequest() {
        RequestTerminationReqDTO request = new RequestTerminationReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(ALIPAY_VENDOR);
        return request;
    }
}
