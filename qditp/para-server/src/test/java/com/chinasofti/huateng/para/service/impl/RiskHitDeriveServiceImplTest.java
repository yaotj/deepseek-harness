package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.model.para.RiskHitDeriveRespDTO;
import com.chinasofti.huateng.para.entity.risk.DerivedHit;
import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.chinasofti.huateng.para.mapper.risk.RiskControlLogMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskGroupMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskGroupRuleMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskRuleMapper;
import com.chinasofti.huateng.para.service.risk.hitderive.RiskHitDeriveHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskHitDeriveServiceImplTest {

    private RiskGroupMapper riskGroupMapper;
    private RiskGroupRuleMapper riskGroupRuleMapper;
    private RiskRuleMapper riskRuleMapper;
    private RiskControlLogMapper riskControlLogMapper;
    private RiskHitDeriveHandler onesideHandler;
    private RiskHitDeriveHandler repeatExitHandler;
    private RiskHitDeriveServiceImpl service;

    @BeforeEach
    void setUp() {
        riskGroupMapper = mock(RiskGroupMapper.class);
        riskGroupRuleMapper = mock(RiskGroupRuleMapper.class);
        riskRuleMapper = mock(RiskRuleMapper.class);
        riskControlLogMapper = mock(RiskControlLogMapper.class);
        onesideHandler = mock(RiskHitDeriveHandler.class);
        repeatExitHandler = mock(RiskHitDeriveHandler.class);
        when(onesideHandler.ruleId()).thenReturn("000003");
        when(repeatExitHandler.ruleId()).thenReturn("000002");
        service = new RiskHitDeriveServiceImpl(
                riskGroupMapper, riskGroupRuleMapper, riskRuleMapper, riskControlLogMapper,
                Arrays.asList(onesideHandler, repeatExitHandler));
        ReflectionTestUtils.setField(service, "groupName", "黑名单直拉规则组");
        ReflectionTestUtils.setField(service, "windowDays", 7);
    }

    @Test
    @DisplayName("组不存在：空转返回成功且计数全 0，不插任何命中行")
    void groupMissingReturnsEmptyResult() {
        when(riskGroupMapper.selectByName("黑名单直拉规则组")).thenReturn(null);

        RiskHitDeriveRespDTO resp = service.derive();

        assertEquals(0, resp.getRuleCount());
        assertEquals(0, resp.getCandidates());
        assertEquals(0, resp.getInserted());
        verify(riskControlLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("组内无规则：空转不派生")
    void groupWithoutRulesSkipsDerive() {
        RiskGroup group = group(65L);
        when(riskGroupMapper.selectByName("黑名单直拉规则组")).thenReturn(group);
        when(riskGroupRuleMapper.selectRuleIdsByGroupId(65L)).thenReturn(Collections.emptyList());

        RiskHitDeriveRespDTO resp = service.derive();

        assertEquals(0, resp.getCandidates());
        verify(riskControlLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("规则无处理器实现：跳过该规则但不阻断其他规则")
    void ruleWithoutHandlerIsSkipped() {
        RiskGroup group = group(65L);
        when(riskGroupMapper.selectByName("黑名单直拉规则组")).thenReturn(group);
        when(riskGroupRuleMapper.selectRuleIdsByGroupId(65L)).thenReturn(Arrays.asList("000099", "000003"));
        when(riskRuleMapper.selectAll()).thenReturn(Arrays.asList(rule("000003", "最大允许单边交易数", 3)));

        LocalDateTime hitTime = LocalDateTime.of(2026, 11, 19, 8, 30, 0);
        when(onesideHandler.derive(any())).thenReturn(Collections.singletonList(
                new DerivedHit("滞留进站", "0426TEST0000000001", null, null, hitTime)));

        RiskHitDeriveRespDTO resp = service.derive();

        assertEquals(2, resp.getRuleCount());
        assertEquals(1, resp.getCandidates());
        assertEquals(1, resp.getInserted());
        assertEquals(1, resp.getRuleDetails().size());

        ArgumentCaptor<RiskControlLog> captor = ArgumentCaptor.forClass(RiskControlLog.class);
        verify(riskControlLogMapper, times(1)).insert(captor.capture());
        RiskControlLog saved = captor.getValue();
        assertEquals("000003", saved.getRuleId());
        assertEquals("0426TEST0000000001", saved.getCardId());
        assertEquals("最大允许单边交易数", saved.getRuleNameSnap());
        assertEquals("0", saved.getRiskStatus());
        assertEquals(hitTime, saved.getRiskHitTime());
    }

    @Test
    @DisplayName("两规则均派生：聚合计数与逐规则明细正确，快照字段带规则现值")
    void bothRulesDeriveAndInsert() {
        RiskGroup group = group(65L);
        when(riskGroupMapper.selectByName("黑名单直拉规则组")).thenReturn(group);
        when(riskGroupRuleMapper.selectRuleIdsByGroupId(65L)).thenReturn(Arrays.asList("000003", "000002"));
        when(riskRuleMapper.selectAll()).thenReturn(Arrays.asList(
                rule("000003", "最大允许单边交易数", 3),
                rule("000002", "出站规则", 3)));

        LocalDateTime t1 = LocalDateTime.of(2026, 11, 19, 9, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 11, 19, 18, 45, 0);
        when(onesideHandler.derive(any())).thenReturn(Arrays.asList(
                new DerivedHit("超时出站", "0426TEST0000000001", "SEQ001", "06220101", t1),
                new DerivedHit("滞留进站", "0426TEST0000000002", null, null, t2)));
        when(repeatExitHandler.derive(any())).thenReturn(Collections.singletonList(
                new DerivedHit("自然日重复出站", "0426TEST0000000003", "SEQ099", null, t2)));

        RiskHitDeriveRespDTO resp = service.derive();

        assertEquals(2, resp.getRuleCount());
        assertEquals(3, resp.getCandidates());
        assertEquals(3, resp.getInserted());
        verify(riskControlLogMapper, times(3)).insert(any());
        assertEquals(2, resp.getRuleDetails().size());
        for (RiskHitDeriveRespDTO.RuleDetail detail : resp.getRuleDetails()) {
            if ("000003".equals(detail.getRuleId())) {
                assertEquals(2, detail.getCandidates());
                assertEquals(2, detail.getInserted());
            } else {
                assertEquals(1, detail.getCandidates());
                assertEquals(1, detail.getInserted());
            }
        }
    }

    @Test
    @DisplayName("单规则派生异常：记 error 继续下一规则，异常规则 inserted 为 0")
    void failingRuleDoesNotBlockOthers() {
        RiskGroup group = group(65L);
        when(riskGroupMapper.selectByName("黑名单直拉规则组")).thenReturn(group);
        when(riskGroupRuleMapper.selectRuleIdsByGroupId(65L)).thenReturn(Arrays.asList("000003", "000002"));
        when(riskRuleMapper.selectAll()).thenReturn(Arrays.asList(
                rule("000003", "最大允许单边交易数", 3),
                rule("000002", "出站规则", 3)));

        when(onesideHandler.derive(any())).thenThrow(new RuntimeException("模拟交易表查询失败"));
        when(repeatExitHandler.derive(any())).thenReturn(Collections.singletonList(
                new DerivedHit("自然日重复出站", "0426TEST0000000003", "SEQ099", null, LocalDateTime.now())));

        RiskHitDeriveRespDTO resp = service.derive();

        assertEquals(1, resp.getCandidates());
        assertEquals(1, resp.getInserted());
        verify(riskControlLogMapper, times(1)).insert(any());
        assertEquals(2, resp.getRuleDetails().size());
        RiskHitDeriveRespDTO.RuleDetail failed = resp.getRuleDetails().stream()
                .filter(d -> "000003".equals(d.getRuleId())).findFirst().orElseThrow();
        assertEquals(0, failed.getInserted());
    }

    private RiskGroup group(Long groupId) {
        RiskGroup group = new RiskGroup();
        group.setGroupId(groupId);
        group.setGroupName("黑名单直拉规则组");
        return group;
    }

    private RiskRule rule(String ruleId, String ruleName, int threshold) {
        RiskRule rule = new RiskRule();
        rule.setRuleId(ruleId);
        rule.setRuleName(ruleName);
        rule.setRuleDesc(ruleName + "描述");
        rule.setRiskLevel(3);
        rule.setRiskLimitValue(BigDecimal.valueOf(threshold));
        return rule;
    }
}
