package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.channel.app.AppNotifyClient;
import com.chinasofti.huateng.facepay.channel.app.AppNotifyProperties;
import com.chinasofti.huateng.facepay.channel.app.AppNotifyResult;
import com.chinasofti.huateng.facepay.entity.F2fNotifyTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@code F2F_NOTIFY_TASK} 的投递执行体。<b>本类只负责「把给定的一批任务发出去」</b>，
 * 不决定「捞哪一批」——捞取由调用方决定，因此同一份投递逻辑能被两种触发方式共用：
 * <ul>
 *   <li>{@code F2fNotifyJob}（本模块 {@code @Scheduled}，全类型、30 秒一轮）；</li>
 *   <li>{@code NoticeAppTaskController}（外部 HTTP 触发，按业务类型分开，
 *       对齐旧模块 {@code /pay/noticeAppTask/**} 的三个 URL）。</li>
 * </ul>
 *
 * <p><b>为什么要单独一个类而不是让 Controller 调 Job</b>：`@Scheduled` 方法是给调度器的入口，
 * 被 HTTP 线程直接调用会让「本轮是谁触发的」在日志里分不清，也会让 Job 从
 * 「只被调度器碰」变成「随时可能被并发进入」。把执行体外提后两个入口各自记自己的日志，
 * Job 仍只被调度器碰一次。
 *
 * <h2>三条约束（与原 {@code F2fNotifyJob} 一致，NEVER 放宽）</h2>
 * <ol>
 *   <li><b>不带 {@code @Transactional}</b>：每笔都要发 HTTP（AGENTS.md §5.2）。</li>
 *   <li><b>逐笔 try/catch</b>：一笔异常不能让整批停摆。</li>
 *   <li><b>URL 未配置也要记失败</b>，NEVER 静默跳过——否则配置漏了没人知道。</li>
 * </ol>
 */
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
     * 一轮投递的计数。{@code due} 是本轮捞到的条数，
     * {@code delivered + retried + errored} 应等于它。
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
    /**
     * 投递全部到期任务（含旧模块没有的 {@code PAY_RESULT}），供 {@code @Scheduled} 使用。
     *
     * <p>扫表本身失败（DB 不可用等）时返回 {@link DeliverStat#empty()} 并记 ERROR，
     * 不抛出——调度器与 HTTP 触发方都不该因为一次扫表失败而中断。
     */
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
     *                   返回 {@code due=0}，<b>不报错</b>——调用方按计数判断即可
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

    /**
     * 按通知类型取目标地址。
     *
     * <p>返回 null 时 {@link AppNotifyClient#post} 会记「通知地址未配置」并判失败，
     * 走退避重试——<b>未知类型不静默丢弃</b>，最终 GIVEUP 时人工能从 {@code LAST_ERROR} 看出原因。
     */
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
