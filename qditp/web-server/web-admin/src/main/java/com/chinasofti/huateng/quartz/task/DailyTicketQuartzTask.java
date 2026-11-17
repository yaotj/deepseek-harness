package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component("dailyTicketQuartzTask")
public class DailyTicketQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(DailyTicketQuartzTask.class);
    private static final String SUCCESS_CODE = "0000";

    private static final String CODE_BUSY = "9998";

    private final DailyTicketClient dailyTicketClient;

    public DailyTicketQuartzTask(DailyTicketClient dailyTicketClient) {
        this.dailyTicketClient = dailyTicketClient;
    }


    public void compensatePayNotify() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            DailyTicketBaseResult response =
                    dailyTicketClient.compensatePayNotify(QuartzTraceUtils.traceHeaders(traceId));
            assertSuccess("日票支付结果通知补偿", response);
        });
    }


    public void compensateAccActiveNotify() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            DailyTicketBaseResult response =
                    dailyTicketClient.compensateAccActiveNotify(QuartzTraceUtils.traceHeaders(traceId));
            assertSuccess("日票ACC发售通知补偿", response);
        });
    }


    public void convergeExpiredTickets() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            DailyTicketBaseResult response =
                    dailyTicketClient.convergeExpiredTickets(QuartzTraceUtils.traceHeaders(traceId));
            if (response == null) {
                throw new IllegalStateException("日票过期收敛接口未返回响应");
            }
            if (CODE_BUSY.equals(response.getRetCode())) {
                log.warn("日票过期收敛被限流，上一轮仍在执行, retMsg={}", response.getRetMsg());
                return;
            }
            if (!SUCCESS_CODE.equals(response.getRetCode())) {
                throw new IllegalStateException("日票过期收敛失败, retCode="
                        + response.getRetCode() + ", retMsg=" + response.getRetMsg());
            }
            log.info("日票过期收敛完成, retMsg={}", response.getRetMsg());
        });
    }

    private void assertSuccess(String bizName, DailyTicketBaseResult response) {
        if (response == null) {
            throw new IllegalStateException(bizName + "接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getRetCode())) {
            throw new IllegalStateException(bizName + "失败, retCode="
                    + response.getRetCode() + ", retMsg=" + response.getRetMsg());
        }
        log.info("{}完成, data={}, retMsg={}", bizName, response.getData(), response.getRetMsg());
    }
}
