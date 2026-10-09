package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住票实例三条 CAS 的前置态与目标态：写反一个方向就是「票被锁死」或「退款后还能过闸」。
 */
class DailyTicketTicketLockWriterTest {

    private DailyTicketInstanceMapper instanceMapper;
    private DailyTicketTicketLockWriter writer;

    @BeforeEach
    void setUp() {
        instanceMapper = mock(DailyTicketInstanceMapper.class);
        writer = new DailyTicketTicketLockWriter(instanceMapper);
    }

    @Test
    void lockForRefundCasFromActivatedToRefundLocked() {
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setId("INST-1");
        when(instanceMapper.updateStatusIfCurrent(eq("INST-1"), eq("ACTIVATED"), eq("REFUND_LOCKED"), any(Date.class)))
                .thenReturn(1);

        assertTrue(writer.lockForRefund(ticket));
    }

    @Test
    void lockForRefundReturnsFalseWhenCasMisses() {
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setId("INST-1");
        when(instanceMapper.updateStatusIfCurrent(anyString(), anyString(), anyString(), any(Date.class)))
                .thenReturn(0);

        assertFalse(writer.lockForRefund(ticket));
    }

    @Test
    void settleOnRefundedCasFromRefundLockedToRefunded() {
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setId("INST-2");
        when(instanceMapper.selectByOrderNo("0E01")).thenReturn(ticket);
        when(instanceMapper.updateStatusIfCurrent(anyString(), anyString(), anyString(), any(Date.class)))
                .thenReturn(1);

        writer.settleOnRefunded("0E01");

        verify(instanceMapper).updateStatusIfCurrent(eq("INST-2"), eq("REFUND_LOCKED"), eq("REFUNDED"), any(Date.class));
    }

    @Test
    void settleOnRefundedSkipsWhenNoInstance() {
        when(instanceMapper.selectByOrderNo("0E-NO-INSTANCE")).thenReturn(null);

        writer.settleOnRefunded("0E-NO-INSTANCE");

        verify(instanceMapper, never()).updateStatusIfCurrent(anyString(), anyString(), anyString(), any(Date.class));
    }

    @Test
    void settleOnRefundedDoesNotThrowWhenCasMisses() {
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setId("INST-3");
        ticket.setTicketStatus("USED");
        when(instanceMapper.selectByOrderNo("0E02")).thenReturn(ticket);
        when(instanceMapper.updateStatusIfCurrent(anyString(), anyString(), anyString(), any(Date.class)))
                .thenReturn(0);

        writer.settleOnRefunded("0E02");

        verify(instanceMapper, times(1))
                .updateStatusIfCurrent(anyString(), anyString(), anyString(), any(Date.class));
    }

    @Test
    void releaseLockCasFromRefundLockedBackToActivated() {
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setId("INST-4");
        when(instanceMapper.selectByOrderNo("0E03")).thenReturn(ticket);

        writer.releaseLock("0E03");

        verify(instanceMapper).updateStatusIfCurrent(eq("INST-4"), eq("REFUND_LOCKED"), eq("ACTIVATED"), any(Date.class));
    }

    @Test
    void releaseLockSkipsWhenNoInstance() {
        when(instanceMapper.selectByOrderNo("0E-NO-INSTANCE")).thenReturn(null);

        writer.releaseLock("0E-NO-INSTANCE");

        verify(instanceMapper, never()).updateStatusIfCurrent(anyString(), anyString(), anyString(), any(Date.class));
    }
}
