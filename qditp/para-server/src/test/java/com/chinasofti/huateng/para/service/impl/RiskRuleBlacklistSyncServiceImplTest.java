package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.model.para.RiskRuleBlacklistSyncRespDTO;
import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import com.chinasofti.huateng.para.entity.risk.RiskHitAggregation;
import com.chinasofti.huateng.para.mapper.risk.RiskControlLogMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskGroupMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskGroupRuleMapper;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskRuleBlacklistSyncServiceImplTest {

    private RiskGroupMapper riskGroupMapper;
    private RiskGroupRuleMapper riskGroupRuleMapper;
    private RiskControlLogMapper riskControlLogMapper;
    private BlacklistClient blacklistClient;
    private RiskRuleBlacklistSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        riskGroupMapper = mock(RiskGroupMapper.class);
        riskGroupRuleMapper = mock(RiskGroupRuleMapper.class);
        riskControlLogMapper = mock(RiskControlLogMapper.class);
        blacklistClient = mock(BlacklistClient.class);
        service = new RiskRuleBlacklistSyncServiceImpl(
                riskGroupMapper, riskGroupRuleMapper, riskControlLogMapper, blacklistClient);
        ReflectionTestUtils.setField(service, "groupName", "黑名单直拉规则组");
        ReflectionTestUtils.setField(service, "windowDays", 7);
        ReflectionTestUtils.setField(service, "maxAddPerRound", 2);
    }

    @Test
    @DisplayName("组不存在：空转返回成功且计数全 0，不触碰黑名单服务")
    void groupMissingReturnsEmptyResult() {
        when(riskGroupMapper.selectByName("黑名单直拉规则组")).thenReturn(null);

        RiskRuleBlacklistSyncRespDTO resp = service.sync();

        assertEquals(0, resp.getCandidates());
        assertEquals(0, resp.getAdded());
        verify(blacklistClient, never()).queryBlackList(any());
        verify(blacklistClient, never()).addBlackList(any());
    }

    @Test
    @DisplayName("组内无规则：空转不聚合")
    void groupWithoutRulesSkipsAggregation() {
        RiskGroup group = group(65L);
        when(riskGroupMapper.selectByName("黑名单直拉规则组")).thenReturn(group);
        when(riskGroupRuleMapper.selectRuleIdsByGroupId(65L)).thenReturn(Collections.emptyList());

        RiskRuleBlacklistSyncRespDTO resp = service.sync();

        assertEquals(0, resp.getRuleCount());
        verify(riskControlLogMapper, never()).selectHitAggregationForBlacklist(anyLong(), anyInt());
    }

    @Test
    @DisplayName("候选三分支：加黑成功 / 已在黑名单跳过 / 加黑失败继续，reason 带组名与命中数")
    void candidatesAreProcessedByBranch() {
        when(riskGroupMapper.selectByName("黑名单直拉规则组")).thenReturn(group(65L));
        when(riskGroupRuleMapper.selectRuleIdsByGroupId(65L)).thenReturn(Arrays.asList("000002", "000003"));
        when(riskControlLogMapper.selectHitAggregationForBlacklist(eq(65L), eq(7)))
                .thenReturn(Arrays.asList(hit("CARD_ADD", "000003", 3), hit("CARD_SKIP", "000003", 5), hit("CARD_FAIL", "000002", 4)));

        when(blacklistClient.queryBlackList(any(QueryBlackListReqDTO.class)))
                .thenAnswer(invocation -> queryResult("0000", "0"))
                .thenAnswer(invocation -> queryResult("0000", "1"))
                .thenAnswer(invocation -> queryResult("0000", "0"));
        when(blacklistClient.addBlackList(any(AddBlackListReqDTO.class)))
                .thenAnswer(invocation -> operateResult("0000", "成功"))
                .thenAnswer(invocation -> operateResult("8001", "黑名单服务内部失败"));

        RiskRuleBlacklistSyncRespDTO resp = service.sync();

        assertEquals(3, resp.getCandidates());
        assertEquals(1, resp.getAdded());
        assertEquals(1, resp.getSkipped());
        assertEquals(1, resp.getFailed());

        ArgumentCaptor<AddBlackListReqDTO> captor = ArgumentCaptor.forClass(AddBlackListReqDTO.class);
        verify(blacklistClient, times(2)).addBlackList(captor.capture());
        AddBlackListReqDTO added = captor.getAllValues().get(0);
        assertEquals("CARD_ADD", added.getCardId());
        assertEquals("01", added.getBlackSource(), "自动加黑 MUST 记 BLACK_SOURCE=01");
        assertEquals("03", added.getBlackCause(), "风控规则命中 MUST 记 BLACK_CAUSE=03");
        assertEquals("riskRuleBlacklistSync", added.getCreateBy());
        assertEquals(true, added.getReason().contains("黑名单直拉规则组"));
        assertEquals(true, added.getReason().contains("[000003]"), "REASON MUST 含稳定规则ID");
        assertEquals(true, added.getReason().contains("3 次"));

        assertEquals("ADDED", resp.getDetails().get(0).getAction());
        assertEquals("SKIPPED_EXISTING", resp.getDetails().get(1).getAction());
        assertEquals("FAILED", resp.getDetails().get(2).getAction());
    }

    @Test
    @DisplayName("单轮上限保险丝：超出部分截断为 TRUNCATED，不调用黑名单服务")
    void maxAddPerRoundTruncates() {
        when(riskGroupMapper.selectByName("黑名单直拉规则组")).thenReturn(group(65L));
        when(riskGroupRuleMapper.selectRuleIdsByGroupId(65L)).thenReturn(Collections.singletonList("000003"));
        when(riskControlLogMapper.selectHitAggregationForBlacklist(eq(65L), eq(7)))
                .thenReturn(Arrays.asList(hit("CARD_1", "000003", 3), hit("CARD_2", "000003", 3), hit("CARD_3", "000003", 3)));
        when(blacklistClient.queryBlackList(any(QueryBlackListReqDTO.class)))
                .thenAnswer(invocation -> queryResult("0000", "0"));
        when(blacklistClient.addBlackList(any(AddBlackListReqDTO.class)))
                .thenAnswer(invocation -> operateResult("0000", "成功"));

        RiskRuleBlacklistSyncRespDTO resp = service.sync();

        assertEquals(2, resp.getAdded());
        assertEquals(1, resp.getTruncated());
        assertEquals("TRUNCATED", resp.getDetails().get(2).getAction());
        verify(blacklistClient, times(2)).addBlackList(any());
    }

    private RiskGroup group(Long groupId) {
        RiskGroup group = new RiskGroup();
        group.setGroupId(groupId);
        group.setGroupName("黑名单直拉规则组");
        return group;
    }

    private RiskHitAggregation hit(String cardId, String ruleId, int hitCount) {
        RiskHitAggregation hit = new RiskHitAggregation();
        hit.setCardId(cardId);
        hit.setRuleId(ruleId);
        hit.setRuleName("最大允许单边交易数");
        hit.setHitCount(hitCount);
        return hit;
    }

    private QueryBlackListResult queryResult(String retCode, String inBlack) {
        QueryBlackListResult result = new QueryBlackListResult();
        result.setRetCode(retCode);
        result.setInBlack(inBlack);
        return result;
    }

    private BlackListOperateResult operateResult(String retCode, String retMsg) {
        BlackListOperateResult result = new BlackListOperateResult();
        result.setRetCode(retCode);
        result.setRetMsg(retMsg);
        return result;
    }
}
