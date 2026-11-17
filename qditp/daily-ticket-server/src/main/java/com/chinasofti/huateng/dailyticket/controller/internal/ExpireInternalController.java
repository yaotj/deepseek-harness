package com.chinasofti.huateng.dailyticket.controller.internal;

import com.chinasofti.huateng.dailyticket.service.expire.DailyTicketExpireService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicBoolean;


@RestController
@RequestMapping("/internal/daily-ticket/expire")
public class ExpireInternalController {

    private static final String CODE_SUCCESS = "0000";

    private static final String CODE_BUSY = "9998";

    private static final Logger log = LoggerFactory.getLogger(ExpireInternalController.class);

    private final DailyTicketExpireService expireService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public ExpireInternalController(DailyTicketExpireService expireService) {
        this.expireService = expireService;
    }

    @PostMapping("/converge")
    public DailyTicketBaseResult converge() {
        if (!running.compareAndSet(false, true)) {
            log.warn("上一轮日票过期收敛仍在执行，本轮跳过");
            return result(CODE_BUSY, "上一轮日票过期收敛仍在执行");
        }
        try {
            DailyTicketExpireService.ExpireResult outcome = expireService.convergeExpired();
            return result(CODE_SUCCESS, "日票过期收敛完成: 候选=" + outcome.scanned()
                    + ", 已过期=" + outcome.expired()
                    + ", 跳过=" + outcome.skipped()
                    + ", 异常=" + outcome.failed());
        } finally {
            running.set(false);
        }
    }

    private static DailyTicketBaseResult result(String retCode, String retMsg) {
        DailyTicketBaseResult response = new DailyTicketBaseResult();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}
