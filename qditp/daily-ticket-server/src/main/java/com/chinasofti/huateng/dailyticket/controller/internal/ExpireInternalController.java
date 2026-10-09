package com.chinasofti.huateng.dailyticket.controller.internal;

import com.chinasofti.huateng.dailyticket.service.expire.DailyTicketExpireService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日票有效期过期收敛入口，由 web-admin 的 Quartz 任务（{@code sys_job} 350「日票过期状态收敛」）触发，不对外暴露。
 *
 * <p>{@link AtomicBoolean} 拒绝并发重入：上一轮没跑完时本轮直接返 {@code 9998}。
 * {@code 9998} 属**限流、不是失败**，上游 Quartz 任务 MUST 只记 WARN 不抛异常，
 * 否则前台调度日志每轮都记红。
 *
 * <p>本类**无鉴权**：与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」冲突，
 * 属沿用本模块现有 {@code /internal/daily-ticket/**} 端点的现状（全部裸暴露、只靠网络可达性隔离），
 * 上线前 MUST 随那批端点统一补齐。
 */
@RestController
@RequestMapping("/internal/daily-ticket/expire")
public class ExpireInternalController {

    private static final String CODE_SUCCESS = "0000";

    /** 上一轮仍在执行，属限流、不是失败。 */
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
