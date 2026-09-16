package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.event.SignResultCommittedEvent;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code PaySignWorkflow} 两个回调入口的**特征测试**（批次 0 护栏）。
 *
 * <p>这里钉住的四条都对应**已发生过的生产事故或已立 ADR 的决定**，拆分时变红即说明踩回旧坑：
 * <ul>
 *   <li><b>签约成功回调 NEVER 直接调 {@code appNotifyService}</b>（ADR-D32 / 2.0.75）：该方法带
 *       {@code @Transactional}，事务内投递通知一旦随后回滚，APP 已收到「签约成功」而
 *       {@code APP_PAY_SIGN_INFO} 并没有那行。现在只发 {@link SignResultCommittedEvent}，
 *       由 {@code SignResultCommittedListener} 在 {@code AFTER_COMMIT} 投递。<b>本类是这条不变量的
 *       唯一自动化断言点</b>——把通知调回来编译照样通过、单跑接口也「看起来正常」。</li>
 *   <li><b>签约失败分支既不落签约主表也不发事件</b>：失败只留流水，
 *       {@code APP_PAY_SIGN_LOG.SIGN_STATUS} 落 {@code FAILED}。</li>
 *   <li><b>解约回调只接受 {@code SCANNING}</b>（白名单，不是「非终态即可」）：{@code PENDING} 尚未做
 *       未结清欠费校验，放行等于绕过前置校验直接删签约记录并通知 APP 解约成功。</li>
 *   <li><b>终态（{@code SUCCESS} / {@code FAILED}）幂等短路返成功</b>：支付中心会重推同一笔，
 *       第二次 MUST 不再删一遍通道、也 MUST NOT 对上游报错引来更多重推。</li>
 * </ul>
 */
class CallbackDomainCharacterizationTest {

    private static final String WALLET_VENDOR = "0B";
    private static final String ALIPAY_VENDOR = "03";
    private static final String SEQ = "0052290701523998";
    private static final String USER = "U-TEST-0002";

    @Test
    void signResultSuccessPublishesCommittedEventAndNeverNotifiesInsideTransaction() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        PaySignCallbackResult result = fixture.service.receiveSignResult(signResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.eventPublisher).publishEvent(any(SignResultCommittedEvent.class));
        verifyNoInteractions(fixture.appNotifyService);
    }

    @Test
    void signResultSuccessInsertsSignedRecord() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        fixture.service.receiveSignResult(signResult("SUCCESS"), "01");

        ArgumentCaptor<PaySignInfo> captor = ArgumentCaptor.forClass(PaySignInfo.class);
        verify(fixture.paySignInfoMapper).insert(captor.capture());
        PaySignInfo inserted = captor.getValue();
        assertEquals(SEQ, inserted.getRequestSignSeq());
        assertEquals(USER, inserted.getThirdUserId());
        assertEquals("SIGNED", inserted.getContractStatus());
    }

    /** 失败分支：不落签约主表、不发事件，流水的 SIGN_STATUS 落 FAILED。 */
    @Test
    void signResultFailureOnlyWritesFailedAuditLog() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        PaySignCallbackResult result = fixture.service.receiveSignResult(signResult("FAILED"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).insert(any());
        verify(fixture.eventPublisher, never()).publishEvent(any(SignResultCommittedEvent.class));
        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals("FAILED", logs.get(logs.size() - 1).getSignStatus());
    }

    /**
     * 钱包（{@code 0B}）签约成功回调<b>必须被接受并落 {@code APP_PAY_SIGN_INFO}</b>。
     *
     * <p>本用例此前叫 {@code walletSignResultCallbackIsRejected}，断言 8001「钱包支付不支持签约结果回调」。
     * 那条契约已于 2026-09-15 作废：钱包也在支付中心建代扣签约，而 {@code APP_PAY_SIGN_INFO}
     * 是 {@code requestPay} 取 {@code requestSignSeq} 的权威来源 —— 拒掉回调等于
     * 「支付中心侧已签约、我方表里零行」的静默不一致。论证见
     * {@code CallbackDomainServiceImpl.receiveSignResult} 方法体内注释。<b>NEVER 回退。</b>
     */
    @Test
    void walletSignResultCallbackIsAcceptedAndPersisted() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        ReceiveSignResultReqDTO request = signResult("SUCCESS");
        request.setPaymentVendor(WALLET_VENDOR);

        PaySignCallbackResult result = fixture.service.receiveSignResult(request, "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        ArgumentCaptor<PaySignInfo> captor = ArgumentCaptor.forClass(PaySignInfo.class);
        verify(fixture.paySignInfoMapper).insert(captor.capture());
        assertEquals(WALLET_VENDOR, captor.getValue().getPaymentVendor());
        assertEquals("SIGNED", captor.getValue().getContractStatus());
        verify(fixture.eventPublisher).publishEvent(any(SignResultCommittedEvent.class));
    }

    @Test
    void terminationCallbackRejectsPendingRequest() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(termination("PENDING"));

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());
    }

    @Test
    void terminationCallbackIsIdempotentOnTerminalStatus() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(termination("SUCCESS"));

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());
    }

    @Test
    void terminationCallbackRejectsUnknownRequest() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(null);

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());
    }

    /** 钱包解绑走 {@code requestAgreeRelease} 同步完成，解约回调只记审计并返幂等成功。 */
    @Test
    void walletTerminationCallbackIsIgnoredButAnsweredSuccessfully() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        ReceiveTerminationResultReqDTO request = terminationResult("SUCCESS");
        request.setPaymentVendor(WALLET_VENDOR);

        BaseRespDTO result = fixture.service.receiveTerminationResult(request, "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.terminationRequestMapper, never()).selectByRequestSignSeq(anyString());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());
    }

    private ReceiveSignResultReqDTO signResult(String status) {
        ReceiveSignResultReqDTO request = new ReceiveSignResultReqDTO();
        request.setRequestSignSeq(SEQ);
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setDisplayAccount("138****8000");
        request.setStatus(status);
        return request;
    }

    private ReceiveTerminationResultReqDTO terminationResult(String status) {
        ReceiveTerminationResultReqDTO request = new ReceiveTerminationResultReqDTO();
        request.setRequestSignSeq(SEQ);
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setStatus(status);
        return request;
    }

    private AppTerminationRequest termination(String terminationStatus) {
        AppTerminationRequest record = new AppTerminationRequest();
        record.setRequestSignSeq(SEQ);
        record.setThirdUserId(USER);
        record.setPaymentVendor(ALIPAY_VENDOR);
        record.setCardId("CARD-2");
        record.setCardType("0441");
        record.setTerminationStatus(terminationStatus);
        return record;
    }
}
