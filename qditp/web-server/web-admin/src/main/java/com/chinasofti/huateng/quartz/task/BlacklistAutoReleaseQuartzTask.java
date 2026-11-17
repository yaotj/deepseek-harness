package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.BlacklistAutoReleaseRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component("blacklistAutoReleaseQuartzTask")
public class BlacklistAutoReleaseQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(BlacklistAutoReleaseQuartzTask.class);

    private final BlacklistClient blacklistClient;

    public BlacklistAutoReleaseQuartzTask(BlacklistClient blacklistClient) {
        this.blacklistClient = blacklistClient;
    }

    public void autoRelease() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            BlacklistAutoReleaseRespDTO result = blacklistClient.autoRelease(
                    null, QuartzTraceUtils.traceHeaders(traceId));
            logResult(result);
        });
    }


    private void logResult(BlacklistAutoReleaseRespDTO result) {
        if (result == null) {
            throw new IllegalStateException("黑名单自动解除接口未返回响应");
        }
        if (result.getFailed() > 0 || result.getUnknown() > 0) {
            log.error("黑名单自动解除存在未闭合记录, MUST 人工核对, scanned={}, released={}, unsettled={},"
                            + " unknown={}, skipped={}, failed={}",
                    result.getScanned(), result.getReleased(), result.getUnsettled(),
                    result.getUnknown(), result.getSkipped(), result.getFailed());
            return;
        }
        if (result.getSkipped() > 0) {
            log.warn("黑名单自动解除存在渠道未知的记录, 这类行不会自愈、MUST 从加黑入口补 channelCode,"
                            + " scanned={}, released={}, unsettled={}, skipped={}",
                    result.getScanned(), result.getReleased(), result.getUnsettled(), result.getSkipped());
            return;
        }
        log.info("黑名单自动解除完成, scanned={}, released={}, unsettled={}, unknown={}, skipped={}, failed={}",
                result.getScanned(), result.getReleased(), result.getUnsettled(),
                result.getUnknown(), result.getSkipped(), result.getFailed());
    }
}
