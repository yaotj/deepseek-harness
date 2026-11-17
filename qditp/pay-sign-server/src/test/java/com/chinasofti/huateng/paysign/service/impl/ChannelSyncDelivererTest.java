package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.domain.SyncStatus;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ChannelSyncDelivererTest {

    private static final String SEQ = "0052290701523994";
    private static final String USER = "U-TEST-0006";
    private static final String ALIPAY_VENDOR = "03";
    private static final String CARD_ID = "CARD-6";
    private static final String CARD_TYPE = "0441";

    private final AccountDomainPort accountDomainPort = mock(AccountDomainPort.class);
    private final AppTerminationRequestMapper terminationRequestMapper = mock(AppTerminationRequestMapper.class);
    private final ChannelSyncDeliverer deliverer =
            new ChannelSyncDeliverer(accountDomainPort, terminationRequestMapper);

    @Test
    void okFallsThroughToSuccessWithoutTouchingRetryCount() {
        when(accountDomainPort.removeChannel(USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE))
                .thenReturn(new RpcOutcome.Ok());

        assertTrue(deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE));

        verify(terminationRequestMapper).updateChannelSyncStatus(eq(SEQ), eq(SyncStatus.SUCCESS.name()),
                any(LocalDateTime.class), anyString());
        verify(terminationRequestMapper, never()).increaseChannelSyncRetryCount(anyString());
        verify(terminationRequestMapper, never()).markChannelSyncManual(anyString(), any(), anyString());
    }

    @Test
    void bizRejectedIncrementsRetryCountBeforeMarkingManual() {
        when(accountDomainPort.removeChannel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.BizRejected("8004", "该通道不存在"));

        assertFalse(deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE));

        InOrder order = inOrder(terminationRequestMapper);
        order.verify(terminationRequestMapper).increaseChannelSyncRetryCount(SEQ);
        order.verify(terminationRequestMapper).markChannelSyncManual(eq(SEQ), any(LocalDateTime.class), anyString());
    }

    @Test
    void bizRejectedRecordsRemoteCodeAndMessage() {
        when(accountDomainPort.removeChannel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.BizRejected("8004", "该通道不存在"));

        deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(terminationRequestMapper).markChannelSyncManual(eq(SEQ), any(LocalDateTime.class), reason.capture());
        assertTrue(reason.getValue().contains("8004"), reason.getValue());
        assertTrue(reason.getValue().contains("该通道不存在"), reason.getValue());
    }

    @Test
    void unreachableStaysRetryableAndNeverGoesManual() {
        when(accountDomainPort.removeChannel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Unreachable(new RuntimeException("connect timed out")));

        assertFalse(deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE));

        verify(terminationRequestMapper).increaseChannelSyncRetryCount(SEQ);
        verify(terminationRequestMapper).updateChannelSyncStatus(eq(SEQ), eq(SyncStatus.FAILED.name()),
                any(LocalDateTime.class), anyString());
        verify(terminationRequestMapper, never()).markChannelSyncManual(anyString(), any(), anyString());
    }

    @Test
    void resultTextIsTruncatedToColumnWidth() {
        when(accountDomainPort.removeChannel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Unreachable(new RuntimeException("x".repeat(4000))));

        deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(terminationRequestMapper).updateChannelSyncStatus(eq(SEQ), eq(SyncStatus.FAILED.name()),
                any(LocalDateTime.class), reason.capture());
        assertTrue(reason.getValue().length() <= 1024, "长度=" + reason.getValue().length());
    }

    @Test
    void persistenceFailureIsSwallowedAndReportedAsNotDelivered() {
        when(accountDomainPort.removeChannel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Ok());
        when(terminationRequestMapper.updateChannelSyncStatus(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("ORA-12899"));

        assertFalse(deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE));
    }

    @Test
    void remoteExceptionIsSwallowed() {
        when(accountDomainPort.removeChannel(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("端口装配异常"));

        assertFalse(deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE));
    }

    @Test
    void rowOverloadDelegatesWithFourFieldsFromSnapshot() {
        when(accountDomainPort.removeChannel(USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE))
                .thenReturn(new RpcOutcome.Ok());

        assertTrue(deliverer.deliver(snapshotRow()));

        verify(accountDomainPort).removeChannel(USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE);
        verify(terminationRequestMapper).updateChannelSyncStatus(eq(SEQ), eq(SyncStatus.SUCCESS.name()),
                any(LocalDateTime.class), anyString());
    }

    private AppTerminationRequest snapshotRow() {
        AppTerminationRequest row = new AppTerminationRequest();
        row.setRequestSignSeq(SEQ);
        row.setThirdUserId(USER);
        row.setPaymentVendor(ALIPAY_VENDOR);
        row.setCardId(CARD_ID);
        row.setCardType(CARD_TYPE);
        return row;
    }
}
