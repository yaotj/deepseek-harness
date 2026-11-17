package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;
import com.chinasofti.huateng.paysign.client.AppNotificationClient;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.port.AppNotifyHttpAdapter;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AppNotifyCompensationCharacterizationTest {

    private static final String SEQ = "0052290701523995";
    private static final String USER = "U-TEST-0005";
    private static final String ALIPAY_VENDOR = "03";

    private final PaySignRequestMapper paySignRequestMapper = mock(PaySignRequestMapper.class);
    private final PaySignInfoMapper paySignInfoMapper = mock(PaySignInfoMapper.class);
    private final AppNotificationClient appNotificationClient = mock(AppNotificationClient.class);

    private final AtomicInteger executorSubmissions = new AtomicInteger();
    private final Executor sameThreadExecutor = task -> {
        executorSubmissions.incrementAndGet();
        task.run();
    };

    private final SignNotifyServiceImpl service = new SignNotifyServiceImpl(
            paySignRequestMapper, paySignInfoMapper,
            new AppNotifyHttpAdapter(appNotificationClient,
                    "http://app.example/ci/app/receiveSignResult",
                    "http://app.example/ci/app/receiveTerminationResultFromItp",
                    "06", "UTF-8", "json", "ITP-PAY-SIGN", "00", ""),
            sameThreadExecutor);

    @Test
    void emptyBatchAnswersSuccessAndNeverTouchesRetryBudget() {
        when(paySignRequestMapper.selectCompensableNotify(anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        CompensateNotifyRespDTO response = service.compensateSignNotify();

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getResultCode());
        assertEquals(0, response.getScanned());
        verify(paySignRequestMapper, never()).increaseRetryCount(anyLong());
    }

    @Test
    void everyRowIncrementsRetryBudgetExactlyOnceBeforeNotifying() {
        when(paySignRequestMapper.selectCompensableNotify(anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(pendingLog(1L), pendingLog(2L)));
        when(appNotificationClient.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        CompensateNotifyRespDTO response = service.compensateSignNotify();

        assertEquals(2, response.getScanned());
        assertEquals(2, response.getSubmitted());
        assertEquals(0, response.getSkipped());
        verify(paySignRequestMapper).increaseRetryCount(1L);
        verify(paySignRequestMapper).increaseRetryCount(2L);

        InOrder order = inOrder(paySignRequestMapper, appNotificationClient);
        order.verify(paySignRequestMapper).increaseRetryCount(1L);
        order.verify(appNotificationClient).notify(any(), any());
    }

    @Test
    void failedNotifyStillConsumesExactlyOneRetryBudget() {
        when(paySignRequestMapper.selectCompensableNotify(anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(pendingLog(1L)));
        when(appNotificationClient.notify(any(), any()))
                .thenReturn(AppNotificationClient.NotificationResult.failure("对端 500"));

        service.compensateSignNotify();

        verify(paySignRequestMapper, times(1)).increaseRetryCount(1L);
    }

    @Test
    void singleRowFailureNeverAbortsTheBatch() {
        when(paySignRequestMapper.selectCompensableNotify(anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(pendingLog(1L), pendingLog(2L), pendingLog(3L)));
        when(appNotificationClient.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());
        when(paySignRequestMapper.increaseRetryCount(2L)).thenThrow(new RuntimeException("行锁超时"));

        CompensateNotifyRespDTO response = service.compensateSignNotify();

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getResultCode());
        assertEquals(3, response.getScanned());
        assertEquals(2, response.getSubmitted());
        assertEquals(1, response.getSkipped());
    }

    @Test
    void resendRejectsBlankSeq() {
        ResendSignNotifyRespDTO response = service.resendSignNotify("  ");

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), response.getResultCode());
        assertFalse(response.isNotified());
        verify(appNotificationClient, never()).notify(any(), any());
    }

    @Test
    void resendRejectsWhenSignInfoMissing() {
        when(paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(null);

        ResendSignNotifyRespDTO response = service.resendSignNotify(SEQ);

        assertEquals(PaySignErrorCodeEnum.RECORD_NOT_EXIST.getCode(), response.getResultCode());
        assertFalse(response.isNotified());
        verify(appNotificationClient, never()).notify(any(), any());
    }

    @Test
    void resendRejectsWhenSignStatusIsNotSigned() {
        when(paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signInfo("UNSIGNED"));

        ResendSignNotifyRespDTO response = service.resendSignNotify(SEQ);

        assertEquals(PaySignErrorCodeEnum.USER_NOT_SIGNED.getCode(), response.getResultCode());
        verify(appNotificationClient, never()).notify(any(), any());
    }

    @Test
    void resendRejectsWhenSignResultLogMissing() {
        when(paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signInfo("SIGNED"));
        when(paySignRequestMapper.selectLatestSignResultBySeq(SEQ)).thenReturn(null);

        ResendSignNotifyRespDTO response = service.resendSignNotify(SEQ);

        assertEquals(PaySignErrorCodeEnum.RECORD_NOT_EXIST.getCode(), response.getResultCode());
        verify(appNotificationClient, never()).notify(any(), any());
    }

    @Test
    void resendNotifiesSynchronouslyAndNeverConsumesRetryBudget() {
        when(paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signInfo("SIGNED"));
        when(paySignRequestMapper.selectLatestSignResultBySeq(SEQ)).thenReturn(pendingLog(9L));
        when(appNotificationClient.notify(any(), any())).thenReturn(AppNotificationClient.NotificationResult.succeeded());

        ResendSignNotifyRespDTO response = service.resendSignNotify(SEQ);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getResultCode());
        assertTrue(response.isNotified());
        assertEquals(SEQ, response.getRequestSignSeq());
        verify(appNotificationClient).notify(any(), any());
        verify(paySignRequestMapper, never()).increaseRetryCount(anyLong());
        assertEquals(0, executorSubmissions.get(), "人工重放 NEVER 走 notifyExecutor");
    }

    @Test
    void resendReportsFailureHonestly() {
        when(paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signInfo("SIGNED"));
        when(paySignRequestMapper.selectLatestSignResultBySeq(SEQ)).thenReturn(pendingLog(9L));
        when(appNotificationClient.notify(any(), any()))
                .thenReturn(AppNotificationClient.NotificationResult.failure("对端超时"));

        ResendSignNotifyRespDTO response = service.resendSignNotify(SEQ);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getResultCode());
        assertFalse(response.isNotified());
        assertEquals("对端超时", response.getNotifyResult());
    }

    private PaySignRequest pendingLog(long id) {
        PaySignRequest record = new PaySignRequest();
        record.setId(id);
        record.setRequestSignSeq(SEQ);
        record.setThirdUserId(USER);
        record.setPaymentVendor(ALIPAY_VENDOR);
        record.setOperationType("RECEIVE_SIGN_RESULT");
        record.setSignStatus("SIGNED");
        record.setNotifyStatus("PENDING");
        record.setNotifyRetryCount(0);
        record.setRequestBody("{\"requestSignSeq\":\"" + SEQ + "\",\"status\":\"SUCCESS\"}");
        return record;
    }

    private PaySignInfo signInfo(String signStatus) {
        PaySignInfo record = new PaySignInfo();
        record.setRequestSignSeq(SEQ);
        record.setThirdUserId(USER);
        record.setPaymentVendor(ALIPAY_VENDOR);
        record.setSignStatus(signStatus);
        return record;
    }
}
