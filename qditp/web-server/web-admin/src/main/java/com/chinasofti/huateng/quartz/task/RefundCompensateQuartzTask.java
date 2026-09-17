package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/** 退款补偿任务：驱动 pay-sign-server 的两个退款相关内部端点。 */
@Component("refundCompensateQuartzTask")
public class RefundCompensateQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(RefundCompensateQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final PaySignClient paySignClient;

    public RefundCompensateQuartzTask(PaySignClient paySignClient) {
        this.paySignClient = paySignClient;
    }

    /** 退款回查补偿。前台调用目标：refundCompensateQuartzTask.compensateRefundQuery()。 */
    public void compensateRefundQuery() {
        invoke("退款回查补偿", paySignClient::compensateRefundQuery, false);
    }

    /** 退款汇总跨表对账补偿。前台调用目标：refundCompensateQuartzTask.compensateRefundSummary()。 */
    public void compensateRefundSummary() {
        invoke("退款汇总跨表对账补偿", paySignClient::compensateRefundSummary, true);
    }

    /** 统一的 traceId 包装：Quartz 进来时 traceId 已由 AbstractQuartzJob.before() 放入 MDC */
    private void invoke(String bizName,
                        Function<Map<String, String>, CompensateNotifyRespDTO> action,
                        boolean skippedExpected) {
        QuartzTraceUtils.runWithTrace(traceId -> invokeOnce(bizName, action, skippedExpected, traceId));
    }

    /** 单条结果看 {@code PAY_REFUND_DETAIL.REFUND_STATUS}。 */
    private void invokeOnce(String bizName,
                            Function<Map<String, String>, CompensateNotifyRespDTO> action,
                            boolean skippedExpected,
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
            if (skippedExpected) {
                log.warn("{}存在未处理记录（其中含不可自愈记录，需人工核对，不代表本轮失败）, scanned={}, submitted={}, skipped={}",
                        bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
            } else {
                log.error("{}存在提交失败记录, scanned={}, submitted={}, skipped={}",
                        bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
            }
        }
        log.info("{}完成, scanned={}, submitted={}, skipped={}",
                bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
    }
}
