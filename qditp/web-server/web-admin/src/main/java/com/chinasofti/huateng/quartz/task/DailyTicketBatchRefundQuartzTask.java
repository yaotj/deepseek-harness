package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;


@Component("dailyTicketBatchRefundQuartzTask")
public class DailyTicketBatchRefundQuartzTask {

    private static final String CODE_BUSY = "9998";

    private static final String SUCCESS_CODE = "0000";

    private static final Logger log = LoggerFactory.getLogger(DailyTicketBatchRefundQuartzTask.class);

    private final DailyTicketClient dailyTicketClient;

    public DailyTicketBatchRefundQuartzTask(DailyTicketClient dailyTicketClient) {
        this.dailyTicketClient = dailyTicketClient;
    }

    public void refundDaily() {
        QuartzTraceUtils.runWithTrace(traceId -> invoke("多日票批量退款(当日)",
                dailyTicketClient::batchRefundDaily, traceId));
    }

    public void refundMonthly() {
        QuartzTraceUtils.runWithTrace(traceId -> invoke("多日票批量退款(月度)",
                dailyTicketClient::batchRefundMonthly, traceId));
    }

    private void invoke(String taskName, Function<Map<String, String>, DailyTicketBaseResult> call, String traceId) {
        DailyTicketBaseResult response = call.apply(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("daily-ticket-server " + taskName + "接口未返回响应");
        }
        if (CODE_BUSY.equals(response.getRetCode())) {
            log.warn("daily-ticket-server {}被限流，上一轮仍在执行, retMsg={}", taskName, response.getRetMsg());
            return;
        }
        if (!SUCCESS_CODE.equals(response.getRetCode())) {
            throw new IllegalStateException("daily-ticket-server " + taskName + "失败: retCode="
                    + response.getRetCode() + ", retMsg=" + response.getRetMsg());
        }
        log.info("daily-ticket-server {}调用成功, retMsg={}", taskName, response.getRetMsg());
    }
}
