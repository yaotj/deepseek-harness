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
import com.chinasofti.huateng.para.service.RiskRuleBlacklistSyncService;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 黑名单直拉规则组自动加黑编排。
 * 口径：窗口期内「同卡+同规则」命中次数 ≥ 该规则 RISK_LIMIT_VALUE → 候选；
 * 已在黑名单跳过（避免 BLACKLIST_OPERATE_LOG 幂等噪音）；单轮加黑数超保险丝截断。
 */
@Service
public class RiskRuleBlacklistSyncServiceImpl implements RiskRuleBlacklistSyncService {

    private static final Logger log = LoggerFactory.getLogger(RiskRuleBlacklistSyncServiceImpl.class);

    private static final String SUCCESS_CODE = "0000";
    private static final String BLACK_SOURCE_AUTO = "01";
    private static final String BLACK_CAUSE_OTHER = "09";
    private static final String CREATE_BY = "riskRuleBlacklistSync";
    private static final String IN_BLACK_FLAG = "1";

    private static final String ACTION_ADDED = "ADDED";
    private static final String ACTION_SKIPPED = "SKIPPED_EXISTING";
    private static final String ACTION_FAILED = "FAILED";
    private static final String ACTION_TRUNCATED = "TRUNCATED";

    private final RiskGroupMapper riskGroupMapper;
    private final RiskGroupRuleMapper riskGroupRuleMapper;
    private final RiskControlLogMapper riskControlLogMapper;
    private final BlacklistClient blacklistClient;

    @Value("${risk.blacklist-sync.group-name:黑名单直拉规则组}")
    private String groupName;

    @Value("${risk.blacklist-sync.window-days:7}")
    private int windowDays;

    @Value("${risk.blacklist-sync.max-add-per-round:50}")
    private int maxAddPerRound;

    public RiskRuleBlacklistSyncServiceImpl(RiskGroupMapper riskGroupMapper,
                                            RiskGroupRuleMapper riskGroupRuleMapper,
                                            RiskControlLogMapper riskControlLogMapper,
                                            BlacklistClient blacklistClient) {
        this.riskGroupMapper = riskGroupMapper;
        this.riskGroupRuleMapper = riskGroupRuleMapper;
        this.riskControlLogMapper = riskControlLogMapper;
        this.blacklistClient = blacklistClient;
    }

    @Override
    public RiskRuleBlacklistSyncRespDTO sync() {
        RiskRuleBlacklistSyncRespDTO resp = new RiskRuleBlacklistSyncRespDTO();
        resp.setGroupName(groupName);
        resp.setWindowDays(windowDays);

        RiskGroup group = riskGroupMapper.selectByName(groupName);
        if (group == null) {
            log.warn("黑名单直拉规则组不存在，自动加黑本轮空转, groupName={}", groupName);
            return resp;
        }

        List<String> ruleIds = riskGroupRuleMapper.selectRuleIdsByGroupId(group.getGroupId());
        resp.setRuleCount(ruleIds == null ? 0 : ruleIds.size());
        if (ruleIds == null || ruleIds.isEmpty()) {
            log.warn("黑名单直拉规则组内无绑定规则，自动加黑本轮空转, groupId={}, groupName={}",
                    group.getGroupId(), groupName);
            return resp;
        }

        List<RiskHitAggregation> candidates =
                riskControlLogMapper.selectHitAggregationForBlacklist(group.getGroupId(), windowDays);
        resp.setCandidates(candidates == null ? 0 : candidates.size());
        if (candidates == null || candidates.isEmpty()) {
            log.info("黑名单直拉规则组窗口期无达阈值命中, groupName={}, windowDays={}", groupName, windowDays);
            return resp;
        }

        for (RiskHitAggregation candidate : candidates) {
            if (resp.getAdded() >= maxAddPerRound) {
                resp.setTruncated(resp.getTruncated() + 1);
                resp.addDetail(detail(candidate, ACTION_TRUNCATED, "超过单轮加黑上限 " + maxAddPerRound));
                continue;
            }
            processCandidate(candidate, resp);
        }

        log.info("黑名单直拉规则组自动加黑完成, groupName={}, windowDays={}, candidates={}, added={}, skipped={}, failed={}, truncated={}",
                groupName, windowDays, resp.getCandidates(), resp.getAdded(), resp.getSkipped(),
                resp.getFailed(), resp.getTruncated());
        return resp;
    }

    private void processCandidate(RiskHitAggregation candidate, RiskRuleBlacklistSyncRespDTO resp) {
        String cardId = candidate.getCardId();
        try {
            if (isAlreadyInBlacklist(cardId)) {
                resp.setSkipped(resp.getSkipped() + 1);
                resp.addDetail(detail(candidate, ACTION_SKIPPED, "已在黑名单"));
                return;
            }
            BlackListOperateResult result = addToBlacklist(candidate);
            if (result != null && SUCCESS_CODE.equals(result.getRetCode())) {
                resp.setAdded(resp.getAdded() + 1);
                resp.addDetail(detail(candidate, ACTION_ADDED, result.getRetMsg()));
                log.info("风控自动加黑成功, cardId={}, ruleId={}, hitCount={}",
                        cardId, candidate.getRuleId(), candidate.getHitCount());
            } else {
                resp.setFailed(resp.getFailed() + 1);
                String msg = result == null ? "黑名单服务未返回" : result.getRetMsg();
                resp.addDetail(detail(candidate, ACTION_FAILED, msg));
                log.error("风控自动加黑失败, cardId={}, ruleId={}, retCode={}, retMsg={}",
                        cardId, candidate.getRuleId(),
                        result == null ? null : result.getRetCode(), msg);
            }
        } catch (Exception e) {
            resp.setFailed(resp.getFailed() + 1);
            resp.addDetail(detail(candidate, ACTION_FAILED, e.getMessage()));
            log.error("风控自动加黑异常, cardId={}, ruleId={}", cardId, candidate.getRuleId(), e);
        }
    }

    private boolean isAlreadyInBlacklist(String cardId) {
        QueryBlackListReqDTO query = new QueryBlackListReqDTO();
        query.setCardId(cardId);
        QueryBlackListResult result = blacklistClient.queryBlackList(query);
        return result != null && SUCCESS_CODE.equals(result.getRetCode())
                && IN_BLACK_FLAG.equals(result.getInBlack());
    }

    private BlackListOperateResult addToBlacklist(RiskHitAggregation candidate) {
        AddBlackListReqDTO request = new AddBlackListReqDTO();
        request.setCardId(candidate.getCardId());
        request.setBlackSource(BLACK_SOURCE_AUTO);
        request.setBlackCause(BLACK_CAUSE_OTHER);
        request.setCreateBy(CREATE_BY);
        request.setReason("规则「" + nullToEmpty(candidate.getRuleName()) + "」" + "窗口期命中 "
                + candidate.getHitCount() + " 次（" + groupName + "自动加黑）");
        return blacklistClient.addBlackList(request);
    }

    private RiskRuleBlacklistSyncRespDTO.Detail detail(RiskHitAggregation candidate, String action, String resultMsg) {
        RiskRuleBlacklistSyncRespDTO.Detail detail = new RiskRuleBlacklistSyncRespDTO.Detail();
        detail.setCardId(candidate.getCardId());
        detail.setRuleId(candidate.getRuleId());
        detail.setRuleName(candidate.getRuleName());
        detail.setHitCount(candidate.getHitCount() == null ? 0 : candidate.getHitCount());
        detail.setAction(action);
        detail.setResultMsg(resultMsg);
        return detail;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
