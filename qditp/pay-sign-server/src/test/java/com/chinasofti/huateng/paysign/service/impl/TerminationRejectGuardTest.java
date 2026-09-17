package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.model.request.CheckFailedOrdersReqDTO;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 护栏：解约拒绝的幂等与冲突分支 NEVER 误发通知；闸机域答 null NEVER 当成「没有失败订单」。 */
class TerminationRejectGuardTest {

    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";
    private static final String ALIPAY_VENDOR = "03";
    private static final String DEFAULT_FAIL_REASON = "存在扣费失败订单";

    /** 入参缺 `requestSignSeq` 直接 8001，**不碰库**。 */
    @Test
    @DisplayName("拒绝解约：入参缺流水号返 8001，不读库不发通知")
    void notifyFailedRejectsBlankSeq() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();

        BaseRespDTO response = fixture.service.notifyTerminationFailed(new NotifyTerminationFailedReqDTO());

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), response.getRetCode());
        verify(fixture.terminationRequestMapper, never()).selectByRequestSignSeq(anyString());
        verify(fixture.appNotifyService, never())
                .asyncNotifyTerminationFailed(any(), any(NotifyTerminationFailedReqDTO.class));
    }

    /** 解约申请不存在 ⇒ 8010，NEVER 兜底成成功。 */
    @Test
    @DisplayName("拒绝解约：解约申请不存在返 8010")
    void notifyFailedRejectsMissingRequest() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(null);

        BaseRespDTO response = fixture.service.notifyTerminationFailed(notifyRequest(null));

        assertEquals(PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND.getCode(), response.getRetCode());
        verify(fixture.terminationRequestMapper, never()).rejectPending(anyString(), anyString(), any());
    }

    /** 已是 `FAILED` ⇒ **幂等成功**，且 NEVER 再发一条通知。 */
    @Test
    @DisplayName("拒绝解约：已是 FAILED 时幂等返 0000，不重发通知也不再 CAS")
    void notifyFailedIsIdempotentOnFailedStatus() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(record("FAILED"));

        BaseRespDTO response = fixture.service.notifyTerminationFailed(notifyRequest(null));

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        verify(fixture.terminationRequestMapper, never()).rejectPending(anyString(), anyString(), any());
        verify(fixture.appNotifyService, never())
                .asyncNotifyTerminationFailed(any(), any(NotifyTerminationFailedReqDTO.class));
    }

    /** 非 PENDING 非 FAILED（如 `SCANNING` 在途）⇒ 8010 拒绝，NEVER 抢着改状态。 */
    @Test
    @DisplayName("拒绝解约：SCANNING 在途时返 8010，不做 CAS")
    void notifyFailedRejectsScanningStatus() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(record("SCANNING"));

        BaseRespDTO response = fixture.service.notifyTerminationFailed(notifyRequest(null));

        assertEquals(PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND.getCode(), response.getRetCode());
        verify(fixture.terminationRequestMapper, never()).rejectPending(anyString(), anyString(), any());
    }

    /** CAS 命中：返 `0000`、发通知，且 `failReason` 空缺时**写进库的是回落值**。 */
    @Test
    @DisplayName("拒绝解约：CAS 命中时返 0000 + 发通知，空 failReason 回落成默认文案")
    void notifyFailedRejectsPendingAndNotifies() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        AppTerminationRequest pending = record("PENDING");
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(pending);
        when(fixture.terminationRequestMapper.rejectPending(eq(SEQ), anyString(), any())).thenReturn(1);

        NotifyTerminationFailedReqDTO request = notifyRequest(null);
        BaseRespDTO response = fixture.service.notifyTerminationFailed(request);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        verify(fixture.terminationRequestMapper)
                .rejectPending(eq(SEQ), eq(DEFAULT_FAIL_REASON), any(LocalDateTime.class));
        verify(fixture.appNotifyService).asyncNotifyTerminationFailed(pending, request);
    }

    /** CAS 落空 ⇒ CONFLICT ⇒ 返错且 NEVER 发通知。 */
    @Test
    @DisplayName("拒绝解约：CAS 落空且库里已 SUCCESS 时返 8010，绝不发失败通知")
    void notifyFailedLosingCasSendsNoNotify() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(record("PENDING"));
        when(fixture.terminationRequestMapper.rejectPending(eq(SEQ), anyString(), any())).thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("SUCCESS");

        BaseRespDTO response = fixture.service.notifyTerminationFailed(notifyRequest("扣费失败"));

        assertEquals(PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND.getCode(), response.getRetCode());
        verify(fixture.appNotifyService, never())
                .asyncNotifyTerminationFailed(any(), any(NotifyTerminationFailedReqDTO.class));
    }

    /** 库层抛异常时**包成 {@link TerminationException} 往外抛**，不是吞掉返错。 */
    @Test
    @DisplayName("拒绝解约：写库异常时抛 TerminationException，不吞成错误码")
    void notifyFailedPropagatesMapperFailure() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ))
                .thenThrow(new RuntimeException("ORA-00060: deadlock detected"));

        assertThrows(TerminationException.class,
                () -> fixture.service.notifyTerminationFailed(notifyRequest(null)));
    }

    /** 三个必填字段任缺一个即 8001，**不打闸机域**。 */
    @Test
    @DisplayName("失败订单核对：缺 requestTime 返 8001，不调 gate-txn-pay")
    void checkFailedOrdersRejectsIncompleteRequest() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        CheckFailedOrdersReqDTO request = new CheckFailedOrdersReqDTO();
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);

        CheckFailedOrdersRespDTO response = fixture.service.checkFailedOrders(request);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), response.getResultCode());
        assertFalse(response.isHasFailedOrder());
        verify(fixture.gateTxnPayClient, never()).hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class));
    }

    /** 闸机域答 `null` ⇒ 9001，**NEVER 当成「没有失败订单」**。 */
    @Test
    @DisplayName("失败订单核对：闸机域答 null 时返 9001，NEVER 退化成「无失败订单」")
    void checkFailedOrdersRejectsNullGateResponse() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        when(fixture.gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class))).thenReturn(null);

        CheckFailedOrdersRespDTO response = fixture.service.checkFailedOrders(checkRequest());

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), response.getResultCode());
        assertFalse(response.isHasFailedOrder());
    }

    /** 闸机域答「有失败订单」⇒ 原样透传 `true` + `0000`。 */
    @Test
    @DisplayName("失败订单核对：闸机域答有失败订单时原样透传 true")
    void checkFailedOrdersPassesThroughPositiveAnswer() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        GateTxnPayFailedOrderRespDTO gateResp = new GateTxnPayFailedOrderRespDTO();
        gateResp.setResultCode("0000");
        gateResp.setHasFailedOrder(true);
        when(fixture.gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class))).thenReturn(gateResp);

        CheckFailedOrdersRespDTO response = fixture.service.checkFailedOrders(checkRequest());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getResultCode());
        assertTrue(response.isHasFailedOrder());
    }

    /** 闸机域不可达（抛异常）⇒ 9001，异常不外溢。 */
    @Test
    @DisplayName("失败订单核对：闸机域抛异常时返 9001，异常不外溢")
    void checkFailedOrdersSurvivesGateThrowing() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        when(fixture.gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenThrow(new RuntimeException("connect timed out"));

        CheckFailedOrdersRespDTO response = fixture.service.checkFailedOrders(checkRequest());

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), response.getResultCode());
        assertFalse(response.isHasFailedOrder());
    }

    private AppTerminationRequest record(String status) {
        AppTerminationRequest request = new AppTerminationRequest();
        request.setRequestSignSeq(SEQ);
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setTerminationStatus(status);
        return request;
    }

    private NotifyTerminationFailedReqDTO notifyRequest(String failReason) {
        NotifyTerminationFailedReqDTO request = new NotifyTerminationFailedReqDTO();
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setRequestSignSeq(SEQ);
        request.setFailReason(failReason);
        return request;
    }

    private CheckFailedOrdersReqDTO checkRequest() {
        CheckFailedOrdersReqDTO request = new CheckFailedOrdersReqDTO();
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setRequestTime(LocalDateTime.now());
        return request;
    }
}

