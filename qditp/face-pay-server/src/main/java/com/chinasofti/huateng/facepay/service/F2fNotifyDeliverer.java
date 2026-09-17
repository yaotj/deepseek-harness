package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.channel.app.AppNotifyClient;
import com.chinasofti.huateng.facepay.channel.app.AppNotifyProperties;
import com.chinasofti.huateng.facepay.channel.app.AppNotifyResult;
import com.chinasofti.huateng.facepay.entity.F2fNotifyTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** {@code F2F_NOTIFY_TASK} 的投递执行体。本类刻意不带 {@code @Transactional}（每笔都要发 HTTP），NEVER 加。 */
@Service
public class F2fNotifyDeliverer {

    private static final Logger log = LoggerFactory.getLogger(F2fNotifyDeliverer.class);

    private final F2fNotifyService notifyService;

    private final AppNotifyClient notifyClient;

    public F2fNotifyDeliverer(F2fNotifyService notifyService, AppNotifyClient notifyClient) {
        this.notifyService = notifyService;
        this.notifyClient = notifyClient;
    }

    /**
     * 一轮投递的计数。
     *
     * @param delivered 投递成功并已置 SUCCESS
     * @param retried   投递失败已安排重试（或已达上限置 GIVEUP）
     * @param errored   处理过程抛异常，状态未推进，下一轮仍会捞到
     */
    public record DeliverStat(int due, int delivered, int retried, int errored) {

        static DeliverStat empty() {
            return new DeliverStat(0, 0, 0, 0);
        }
    }
    /** 投递全部到期任务（含旧模块没有的 {@code PAY_RESULT}），供 {@code @Scheduled} 使用。 */
    public DeliverStat deliverDue(int limit) {
        List<F2fNotifyTask> tasks;
        try {
            tasks = notifyService.loadDueTasks(limit);
        } catch (RuntimeException e) {
            log.error("通知任务扫表失败, limit={}", limit, e);
            return DeliverStat.empty();
        }
        return deliver(tasks, "全部类型");
    }

    /**
     * 只投递指定类型的到期任务，供 {@code /pay/noticeAppTask/**} 使用。
     *
     * @param notifyType 取值见 {@code F2fNotifyService.TYPE_*}；传未知值时扫不到任何行，
     */
    public DeliverStat deliverDueByType(String notifyType, int limit) {
        List<F2fNotifyTask> tasks;
        try {
            tasks = notifyService.loadDueTasksByType(notifyType, limit);
        } catch (RuntimeException e) {
            log.error("通知任务扫表失败, notifyType={}, limit={}", notifyType, limit, e);
            return DeliverStat.empty();
        }
        return deliver(tasks, notifyType);
    }

    private DeliverStat deliver(List<F2fNotifyTask> tasks, String scope) {
        if (tasks.isEmpty()) {
            return DeliverStat.empty();
        }
        int delivered = 0;
        int retried = 0;
        int errored = 0;
        for (F2fNotifyTask task : tasks) {
            try {
                AppNotifyResult result = notifyClient.post(urlOf(task.getNotifyType()), task.getPayload());
                if (result.delivered()) {
                    notifyService.markSuccess(task.getId());
                    delivered++;
                } else {
                    notifyService.markFailure(task, result.failureReason());
                    retried++;
                }
            } catch (RuntimeException e) {
                errored++;
                log.error("通知投递处理异常, id={}, notifyType={}, orderNo={}",
                        task.getId(), task.getNotifyType(), task.getOrderNo(), e);
            }
        }
        log.info("通知投递完成, 范围={}, 到期={}, 已投递={}, 待重试={}, 异常={}",
                scope, tasks.size(), delivered, retried, errored);
        return new DeliverStat(tasks.size(), delivered, retried, errored);
    }

    /** 按通知类型取目标地址。 */
    private String urlOf(String notifyType) {
        AppNotifyProperties properties = notifyClient.properties();
        return switch (notifyType) {
            case F2fNotifyService.TYPE_TAKE_TICKET_OK -> properties.getTakeTicketOkUrl();
            case F2fNotifyService.TYPE_TAKE_TICKET_FAIL -> properties.getTakeTicketFailUrl();
            case F2fNotifyService.TYPE_REFUND_RESULT -> properties.getRefundResultUrl();
            case F2fNotifyService.TYPE_PAY_RESULT -> properties.getPayResultUrl();
            default -> null;
        };
    }
}
