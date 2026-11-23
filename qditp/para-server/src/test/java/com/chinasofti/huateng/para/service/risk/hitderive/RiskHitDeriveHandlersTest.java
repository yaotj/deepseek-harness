package com.chinasofti.huateng.para.service.risk.hitderive;

import com.chinasofti.huateng.para.entity.risk.DerivedHit;
import com.chinasofti.huateng.para.entity.risk.RepeatExitDay;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.chinasofti.huateng.para.entity.risk.StuckEntryEvent;
import com.chinasofti.huateng.para.entity.risk.TimeoutExitEvent;
import com.chinasofti.huateng.para.mapper.risk.RiskHitDeriveMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskHitDeriveHandlersTest {

    private RiskHitDeriveMapper mapper;
    private OnesideHitDeriveHandler onesideHandler;
    private RepeatExitHitDeriveHandler repeatExitHandler;

    @BeforeEach
    void setUp() {
        mapper = mock(RiskHitDeriveMapper.class);
        onesideHandler = new OnesideHitDeriveHandler(mapper);
        repeatExitHandler = new RepeatExitHitDeriveHandler(mapper);
        ReflectionTestUtils.setField(onesideHandler, "windowDays", 7);
        ReflectionTestUtils.setField(onesideHandler, "stuckHours", 24);
        ReflectionTestUtils.setField(repeatExitHandler, "windowDays", 1);
    }

    @Test
    @DisplayName("000003：超时出站与滞留进站两种事件各产一行，字段映射正确")
    void onesideDerivesBothKinds() {
        TimeoutExitEvent timeout = new TimeoutExitEvent();
        timeout.setCardId("0426TEST0000000001");
        timeout.setDeviceId("06220101");
        timeout.setHandleDateTime("20261119083000");
        timeout.setTicketTransSeq("SEQ001");
        StuckEntryEvent stuck = new StuckEntryEvent();
        stuck.setCardId("0426TEST0000000002");
        stuck.setGateInTime("20261118090000");
        stuck.setGateInStation("0101");
        when(mapper.selectTimeoutExits(eq("000003"), anyInt())).thenReturn(Collections.singletonList(timeout));
        when(mapper.selectStuckEntries(eq("000003"), anyInt())).thenReturn(Collections.singletonList(stuck));

        List<DerivedHit> hits = onesideHandler.derive(new RiskRule());

        assertEquals(2, hits.size());
        DerivedHit first = hits.get(0);
        assertEquals("超时出站", first.getKind());
        assertEquals("0426TEST0000000001", first.getCardId());
        assertEquals("SEQ001", first.getUserOrderNo());
        assertEquals("06220101", first.getDeviceId());
        assertEquals("2026-11-19T08:30", first.getRiskHitTime().toString());
        DerivedHit second = hits.get(1);
        assertEquals("滞留进站", second.getKind());
        assertNull(second.getUserOrderNo());
        assertEquals("2026-11-18T09:00", second.getRiskHitTime().toString());
        verify(mapper).selectTimeoutExits("000003", 7);
        verify(mapper).selectStuckEntries("000003", 24);
    }

    @Test
    @DisplayName("000002：阈值取规则 RISK_LIMIT_VALUE，命中带当日末次出站单号")
    void repeatExitUsesRuleThreshold() {
        RepeatExitDay day = new RepeatExitDay();
        day.setCardId("0426TEST0000000003");
        day.setTxnDate("20261119");
        day.setExitCount(4);
        day.setLastExitTime("20261119184500");
        day.setLastExitSeq("SEQ099");
        when(mapper.selectRepeatExitDays(eq("000002"), eq(3), anyInt())).thenReturn(Collections.singletonList(day));

        RiskRule rule = new RiskRule();
        rule.setRuleId("000002");
        rule.setRiskLimitValue(BigDecimal.valueOf(3));
        List<DerivedHit> hits = repeatExitHandler.derive(rule);

        assertEquals(1, hits.size());
        assertEquals("自然日重复出站", hits.get(0).getKind());
        assertEquals("SEQ099", hits.get(0).getUserOrderNo());
        assertEquals("2026-11-19T18:45", hits.get(0).getRiskHitTime().toString());
        verify(mapper).selectRepeatExitDays("000002", 3, 1);
    }

    @Test
    @DisplayName("000002：规则阈值为空时兜底为 1，不抛异常")
    void repeatExitFallsBackWhenThresholdMissing() {
        when(mapper.selectRepeatExitDays(eq("000002"), eq(1), anyInt())).thenReturn(Collections.emptyList());

        RiskRule rule = new RiskRule();
        rule.setRuleId("000002");
        rule.setRiskLimitValue(null);
        List<DerivedHit> hits = repeatExitHandler.derive(rule);

        assertEquals(0, hits.size());
        verify(mapper).selectRepeatExitDays("000002", 1, 1);
    }
}
