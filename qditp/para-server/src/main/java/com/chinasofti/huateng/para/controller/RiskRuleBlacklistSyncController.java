package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.model.para.RiskRuleBlacklistSyncRespDTO;
import com.chinasofti.huateng.para.service.RiskRuleBlacklistSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控内部端点：黑名单直拉规则组自动加黑（web-admin Quartz 调度触发）。
 * 返回结构化统计，异常按 9999 抛出由调度侧告警。
 */
@RestController
@RequestMapping("/internal/risk")
public class RiskRuleBlacklistSyncController {

    private static final Logger log = LoggerFactory.getLogger(RiskRuleBlacklistSyncController.class);

    private final RiskRuleBlacklistSyncService riskRuleBlacklistSyncService;

    public RiskRuleBlacklistSyncController(RiskRuleBlacklistSyncService riskRuleBlacklistSyncService) {
        this.riskRuleBlacklistSyncService = riskRuleBlacklistSyncService;
    }

    @PostMapping("/rule-blacklist-sync")
    public RiskRuleBlacklistSyncRespDTO sync() {
        RiskRuleBlacklistSyncRespDTO result = riskRuleBlacklistSyncService.sync();
        log.info("风险规则命中自动加黑单轮结果, groupName={}, candidates={}, added={}, skipped={}, failed={}, truncated={}",
                result.getGroupName(), result.getCandidates(), result.getAdded(),
                result.getSkipped(), result.getFailed(), result.getTruncated());
        return result;
    }
}
