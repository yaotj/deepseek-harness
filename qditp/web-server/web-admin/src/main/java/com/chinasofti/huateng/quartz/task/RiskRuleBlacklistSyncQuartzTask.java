package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.para.RiskRuleBlacklistSyncRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 黑名单直拉规则组自动加黑调度入口。
 * sys_job: riskRuleBlacklistSyncQuartzTask.sync()
 */
@Component("riskRuleBlacklistSyncQuartzTask")
public class RiskRuleBlacklistSyncQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(RiskRuleBlacklistSyncQuartzTask.class);

    private final ParaClient paraClient;

    public RiskRuleBlacklistSyncQuartzTask(ParaClient paraClient) {
        this.paraClient = paraClient;
    }

    public void sync() {
        QuartzTraceUtils.runWithTrace(this::syncOnce);
    }

    private void syncOnce(String traceId) {
        RiskRuleBlacklistSyncRespDTO response = paraClient.riskRuleBlacklistSync(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("para-server 风险规则命中自动加黑接口未返回响应");
        }
        if (response.getFailed() > 0) {
            log.error("风险规则命中自动加黑存在失败记录, MUST 人工核对, groupName={}, candidates={}, added={}, skipped={}, failed={}",
                    response.getGroupName(), response.getCandidates(), response.getAdded(),
                    response.getSkipped(), response.getFailed());
        }
        log.info("风险规则命中自动加黑完成, groupName={}, windowDays={}, ruleCount={}, candidates={}, added={}, skipped={}, failed={}, truncated={}",
                response.getGroupName(), response.getWindowDays(), response.getRuleCount(),
                response.getCandidates(), response.getAdded(), response.getSkipped(),
                response.getFailed(), response.getTruncated());
    }
}
