package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/** APP 通知补偿任务：把签约结果通知与解约结果通知里「没通知成功」的记录重新推给 APP。 */
@Component("notifyCompensateQuartzTask")
public class NotifyCompensateQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(NotifyCompensateQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final PaySignClient paySignClient;

    public NotifyCompensateQuartzTask(PaySignClient paySignClient) {
        this.paySignClient = paySignClient;
    }

    /**
     * 签约结果通知补偿。前台调用目标：notifyCompensateQuartzTask.compensateSignNotify()。
     */
    public void compensateSignNotify() {
        invoke("签约结果通知补偿", paySignClient::compensateSignNotify);
    }

    /**
     * 解约结果通知补偿。前台调用目标：notifyCompensateQuartzTask.compensateTerminationNotify()。
     */
    public void compensateTerminationNotify() {
        invoke("解约结果通知补偿", paySignClient::compensateTerminationNotify);
    }

    /** 统一的 traceId 包装：Quartz 进来时 traceId 已由 AbstractQuartzJob.before() 放入 MDC */
    private void invoke(String bizName, Function<Map<String, String>, CompensateNotifyRespDTO> action) {
        QuartzTraceUtils.runWithTrace(traceId -> invokeOnce(bizName, action, traceId));
    }

    /** 静默返回会让调度日志记成成功。 */
    private void invokeOnce(String bizName,
                            Function<Map<String, String>, CompensateNotifyRespDTO> action,
                            String traceId) {
        CompensateNotifyRespDTO response = action.apply(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException(bizName + "接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getResultCode())) {
            throw new IllegalStateException(bizName + "失败, resultCode=" + response.getResultCode()
                    + ", resultMsg=" + response.getResultMsg());
        }
        if (response.getSkipped() > 0) {
            log.error("{}存在提交失败记录, scanned={}, submitted={}, skipped={}",
                    bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
        }
        log.info("{}完成, scanned={}, submitted={}, skipped={}",
                bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
    }
}
