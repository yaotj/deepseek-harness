package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.para.RiskHitDeriveRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 风控命中批量派生调度入口（IT-086 方案 B，给 IT-083 自动加黑补命中输入）。
 * sys_job: riskHitDeriveQuartzTask.derive()；触发时点早于加黑任务（425 在 :05/:15/...，本任务 :02/:12/...）。
 * job_id 由迁移脚本动态生成（max+5，不硬编码）。
 */
@Component("riskHitDeriveQuartzTask")
public class RiskHitDeriveQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(RiskHitDeriveQuartzTask.class);

    private final ParaClient paraClient;

    public RiskHitDeriveQuartzTask(ParaClient paraClient) {
        this.paraClient = paraClient;
    }

    public void derive() {
        QuartzTraceUtils.runWithTrace(this::deriveOnce);
    }

    private void deriveOnce(String traceId) {
        RiskHitDeriveRespDTO response = paraClient.riskHitDerive(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("para-server 风控命中批量派生接口未返回响应");
        }
        log.info("风控命中批量派生完成, groupName={}, windowDays={}, ruleCount={}, candidates={}, inserted={}",
                response.getGroupName(), response.getWindowDays(), response.getRuleCount(),
                response.getCandidates(), response.getInserted());
    }
}
