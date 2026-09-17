package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.BlacklistReleaseCandidateDTO;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** 黑名单「可解除性」盘点任务。 */
@Component("blacklistReleaseInspectQuartzTask")
public class BlacklistReleaseInspectQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(BlacklistReleaseInspectQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final BlacklistClient blacklistClient;

    public BlacklistReleaseInspectQuartzTask(BlacklistClient blacklistClient) {
        this.blacklistClient = blacklistClient;
    }

    /** 盘点黑名单可解除性。前台调用目标：blacklistReleaseInspectQuartzTask.inspect()。 */
    public void inspect() {
        QuartzTraceUtils.runWithTrace(this::inspectOnce);
    }

    /** 静默返回会让调度日志记成成功。 */
    private void inspectOnce(String traceId) {
        BlacklistReleaseInspectRespDTO response = blacklistClient.inspectReleasable(
                QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("黑名单可解除性盘点接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getResultCode())) {
            throw new IllegalStateException("黑名单可解除性盘点失败, resultCode=" + response.getResultCode()
                    + ", resultMsg=" + response.getResultMsg());
        }

        // unknown 是「至少一个欠费源没查成功」，事实不明，本次盘点结论对这些记录不可用，需人工看一眼。
        if (response.getUnknown() > 0) {
            log.error("黑名单可解除性盘点存在查询失败记录, MUST 人工核对, scanned={}, unknown={}",
                    response.getScanned(), response.getUnknown());
        }
        log.info("黑名单可解除性盘点完成, scanned={}, settled={}, unsettled={}, unknown={}",
                response.getScanned(), response.getSettled(), response.getUnsettled(), response.getUnknown());
        logSettledDetails(response.getDetails());
    }

    /** 把「欠费已结清」的记录逐条打出来，供人工判断是否该解除。 */
    private void logSettledDetails(List<BlacklistReleaseCandidateDTO> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (BlacklistReleaseCandidateDTO detail : details) {
            if (!"SETTLED".equals(detail.getSettleStatus())) {
                continue;
            }
            log.info("黑名单欠费已结清待人工判断, cardId={}, thirdUserId={}, createTime={}, reason={}",
                    detail.getCardId(), detail.getThirdUserId(), detail.getCreateTime(), detail.getReason());
        }
    }
}
