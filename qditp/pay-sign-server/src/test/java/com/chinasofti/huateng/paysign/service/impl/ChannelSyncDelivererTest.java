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

/**
 * {@link ChannelSyncDeliverer} 的护栏（2026-09-16）。
 *
 * <p><b>此前一行未跑</b>：`TerminationCallbackTrunkCharacterizationTest` 只 `verify` 了它被调用，
 * 而夹具里它是 mock。这个类是 ADR-D48 整套 outbox 的**执行点**，有两个调用方
 * （回调收口的快速路径 + 扫表补偿），三分支处置口径「MUST 只有一处实现」。
 *
 * <p>钉住的不变量：
 * <ul>
 *   <li><b>{@code Ok} → 落 {@code SUCCESS} 且 NEVER 递增重试次数</b>（那是失败侧的计数）。</li>
 *   <li><b>{@code BizRejected} → MUST 先 {@code increaseChannelSyncRetryCount} 再
 *       {@code markChannelSyncManual}</b>：后者的 CAS 要求前置态已是 {@code FAILED}，
 *       顺序写反 CAS 命中 0 行 —— 表现不是报错，而是<b>这一笔永久留在补偿队列里无限重推</b>。
 *       本类用 {@code InOrder} 钉住这个顺序，因为它是纯顺序耦合、编译器与单条 verify 都发现不了。</li>
 *   <li><b>{@code Unreachable} → 只 +1 并落 {@code FAILED}，NEVER 转人工</b>（可重试）。</li>
 *   <li><b>落库自身异常 NEVER 上抛</b>：两个调用方都在「本地已提交」之后调它，抛出去只会让
 *       上游误判失败；滞留状态由下一轮补偿捞走。</li>
 *   <li><b>{@code CHANNEL_SYNC_RESULT} 是 VARCHAR2(1024)，写入 MUST 截断</b>，否则 ORA-12899。</li>
 * </ul>
 */
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

    /** 业务拒绝：两句 CAS 的顺序就是不变量本身 —— 写反了不报错，只会永久留在补偿队列。 */
    @Test
    void bizRejectedIncrementsRetryCountBeforeMarkingManual() {
        when(accountDomainPort.removeChannel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.BizRejected("8004", "该通道不存在"));

        assertFalse(deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE));

        InOrder order = inOrder(terminationRequestMapper);
        order.verify(terminationRequestMapper).increaseChannelSyncRetryCount(SEQ);
        order.verify(terminationRequestMapper).markChannelSyncManual(eq(SEQ), any(LocalDateTime.class), anyString());
    }

    /** 转人工的原因里 MUST 带上对端的 retCode / retMsg，否则运维拿不到可操作信息。 */
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

    /** 不可达：只 +1 并落 FAILED 留给下一轮，NEVER 转人工。 */
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

    /** CHANNEL_SYNC_RESULT 是 VARCHAR2(1024)：超长原因 MUST 截断，否则 ORA-12899。 */
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

    /** 落库自身抛异常时 NEVER 上抛：调用方都在本地已提交之后调它。 */
    @Test
    void persistenceFailureIsSwallowedAndReportedAsNotDelivered() {
        when(accountDomainPort.removeChannel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Ok());
        when(terminationRequestMapper.updateChannelSyncStatus(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("ORA-12899"));

        assertFalse(deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE));
    }

    /** 远端调用本身抛异常同样被兜住，NEVER 穿到调用方。 */
    @Test
    void remoteExceptionIsSwallowed() {
        when(accountDomainPort.removeChannel(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("端口装配异常"));

        assertFalse(deliverer.deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE));
    }

    /** 补偿侧重载：四要素直接取自扫表快照行，MUST 与五参重载走同一段处置。 */
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
