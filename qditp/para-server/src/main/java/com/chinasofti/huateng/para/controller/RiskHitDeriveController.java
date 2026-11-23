package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.model.para.RiskHitDeriveRespDTO;
import com.chinasofti.huateng.para.service.RiskHitDeriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控内部端点：命中批量派生（IT-086 方案 B，web-admin Quartz 调度触发）。
 * 返回结构化统计，异常按 9999 抛出由调度侧告警。
 */
@RestController
@RequestMapping("/internal/risk")
public class RiskHitDeriveController {

    private static final Logger log = LoggerFactory.getLogger(RiskHitDeriveController.class);

    private final RiskHitDeriveService riskHitDeriveService;

    public RiskHitDeriveController(RiskHitDeriveService riskHitDeriveService) {
        this.riskHitDeriveService = riskHitDeriveService;
    }

    @PostMapping("/hit-derive")
    public RiskHitDeriveRespDTO derive() {
        RiskHitDeriveRespDTO result = riskHitDeriveService.derive();
        log.info("风控命中批量派生单轮结果, groupName={}, ruleCount={}, candidates={}, inserted={}",
                result.getGroupName(), result.getRuleCount(), result.getCandidates(), result.getInserted());
        return result;
    }
}
