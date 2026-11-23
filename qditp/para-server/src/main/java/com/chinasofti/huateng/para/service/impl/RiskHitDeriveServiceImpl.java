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
import com.chinasofti.huateng.para.service.RiskHitDeriveService;
import com.chinasofti.huateng.para.service.risk.hitderive.RiskHitDeriveHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 命中批量派生编排：对「黑名单直拉规则组」绑定的每条规则找对应 Handler 派生候选并落库。
 * 与 IT-083 加黑任务同域同组：派生补输入、加黑做处置，职责不重叠。
 * 审计表只增不改：RISK_HANDLE_* 一律不碰。
 */
@Service
public class RiskHitDeriveServiceImpl implements RiskHitDeriveService {

    private static final Logger log = LoggerFactory.getLogger(RiskHitDeriveServiceImpl.class);

    /** 派生命中初始风险状态（沿用旧系统台账取值 0=未处理）。 */
    private static final String RISK_STATUS_INIT = "0";

    @Value("${risk.blacklist-sync.group-name:黑名单直拉规则组}")
    private String groupName;

    @Value("${risk.hit-derive.window-days:7}")
    private int windowDays;

    private final RiskGroupMapper riskGroupMapper;
    private final RiskGroupRuleMapper riskGroupRuleMapper;
    private final RiskRuleMapper riskRuleMapper;
    private final RiskControlLogMapper riskControlLogMapper;
    private final Map<String, RiskHitDeriveHandler> handlerMap;

    public RiskHitDeriveServiceImpl(RiskGroupMapper riskGroupMapper,
                                    RiskGroupRuleMapper riskGroupRuleMapper,
                                    RiskRuleMapper riskRuleMapper,
                                    RiskControlLogMapper riskControlLogMapper,
                                    List<RiskHitDeriveHandler> handlers) {
        this.riskGroupMapper = riskGroupMapper;
        this.riskGroupRuleMapper = riskGroupRuleMapper;
        this.riskRuleMapper = riskRuleMapper;
        this.riskControlLogMapper = riskControlLogMapper;
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(RiskHitDeriveHandler::ruleId, Function.identity()));
    }

    @Override
    public RiskHitDeriveRespDTO derive() {
        RiskHitDeriveRespDTO resp = new RiskHitDeriveRespDTO();
        resp.setGroupName(groupName);
        resp.setWindowDays(windowDays);

        RiskGroup group = riskGroupMapper.selectByName(groupName);
        if (group == null) {
            log.warn("黑名单直拉规则组不存在，命中派生本轮空转, groupName={}", groupName);
            return resp;
        }

        List<String> ruleIds = riskGroupRuleMapper.selectRuleIdsByGroupId(group.getGroupId());
        resp.setRuleCount(ruleIds == null ? 0 : ruleIds.size());
        if (ruleIds == null || ruleIds.isEmpty()) {
            log.warn("黑名单直拉规则组内无绑定规则，命中派生本轮空转, groupId={}, groupName={}",
                    group.getGroupId(), groupName);
            return resp;
        }

        Map<String, RiskRule> ruleMap = riskRuleMapper.selectAll().stream()
                .collect(Collectors.toMap(RiskRule::getRuleId, Function.identity(), (a, b) -> a));

        for (String ruleId : ruleIds) {
            RiskHitDeriveHandler handler = handlerMap.get(ruleId);
            if (handler == null) {
                log.warn("规则无派生处理器实现，跳过, ruleId={}", ruleId);
                continue;
            }
            RiskRule rule = ruleMap.get(ruleId);
            if (rule == null) {
                log.warn("规则绑定指向不存在的规则，跳过, ruleId={}", ruleId);
                continue;
            }
            deriveByRule(rule, handler, resp);
        }

        log.info("风控命中批量派生完成, groupName={}, windowDays={}, ruleCount={}, candidates={}, inserted={}",
                groupName, windowDays, resp.getRuleCount(), resp.getCandidates(), resp.getInserted());
        return resp;
    }

    private void deriveByRule(RiskRule rule, RiskHitDeriveHandler handler, RiskHitDeriveRespDTO resp) {
        RiskHitDeriveRespDTO.RuleDetail detail = new RiskHitDeriveRespDTO.RuleDetail();
        detail.setRuleId(rule.getRuleId());
        detail.setRuleName(rule.getRuleName());
        try {
            List<DerivedHit> hits = handler.derive(rule);
            detail.setCandidates(hits.size());
            for (DerivedHit hit : hits) {
                RiskControlLog record = new RiskControlLog();
                record.setCardId(hit.getCardId());
                record.setUserOrderNo(hit.getUserOrderNo());
                record.setRuleId(rule.getRuleId());
                record.setRuleNameSnap(rule.getRuleName());
                record.setRuleDescSnap(rule.getRuleDesc());
                record.setRiskLevelSnap(rule.getRiskLevel());
                record.setRiskStatus(RISK_STATUS_INIT);
                record.setRiskDeviceId(hit.getDeviceId());
                record.setRiskHitTime(hit.getRiskHitTime());
                riskControlLogMapper.insert(record);
                detail.setInserted(detail.getInserted() + 1);
            }
        } catch (Exception e) {
            log.error("规则命中派生失败, ruleId={}, ruleName={}", rule.getRuleId(), rule.getRuleName(), e);
        }
        detail.setKind(handler.getClass().getSimpleName());
        resp.addRuleDetail(detail);
        resp.setCandidates(resp.getCandidates() + detail.getCandidates());
        resp.setInserted(resp.getInserted() + detail.getInserted());
    }
}
