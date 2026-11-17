package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component("blacklistChannelSyncQuartzTask")
public class BlacklistChannelSyncQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(BlacklistChannelSyncQuartzTask.class);

    private final BlacklistClient blacklistClient;

    public BlacklistChannelSyncQuartzTask(BlacklistClient blacklistClient) {
        this.blacklistClient = blacklistClient;
    }

    public void compensateAdd() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            OutboxScan.Result result = blacklistClient.compensateChannelSyncAdd(
                    null, QuartzTraceUtils.traceHeaders(traceId));
            logResult("加黑", result);
        });
    }

    public void compensateRelease() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            OutboxScan.Result result = blacklistClient.compensateChannelSyncRelease(
                    null, QuartzTraceUtils.traceHeaders(traceId));
            logResult("解黑", result);
        });
    }


    private void logResult(String direction, OutboxScan.Result result) {
        if (result == null) {
            throw new IllegalStateException(direction + "通知补偿接口未返回响应");
        }
        if (result.failed() > 0) {
            log.error("黑名单{}通知补偿存在失败记录, MUST 人工核对是否已落 REJECTED 终态, scanned={}, success={}, failed={}",
                    direction, result.scanned(), result.success(), result.failed());
            return;
        }
        log.info("黑名单{}通知补偿完成, scanned={}, success={}, failed={}",
                direction, result.scanned(), result.success(), result.failed());
    }
}
