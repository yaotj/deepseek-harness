package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.service.F2fNotifyDeliverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 出向通知投递任务。<b>这是 {@code F2F_NOTIFY_TASK} 的常规出口</b>——
 * 没有它，{@code F2fNotifyService.enqueue} 落的任务只会堆在表里。
 *
 * <p><b>投递逻辑本身已外提到 {@link F2fNotifyDeliverer}</b>，因为
 * {@code /pay/noticeAppTask/**} 那三个外部触发端点要复用同一份实现（旧模块的重投入口，
 * 见 {@code NoticeAppTaskController}）。本类只剩「多久扫一次、单轮多少条」两个决策。
 *
 * <h2>相对旧实现的结构性差异</h2>
 * <p>旧实现在设备请求线程里同步发 HTTP，失败置 {@code NOTICE_FAIL} 就结束，
 * 而重推方法 {@code sendNoticeAppTakeTicketRecord} <b>没有任何 {@code @Scheduled} 绑定</b>，
 * 要靠外部 web-server 的 Quartz 去调。两个后果：设备白等一个 HTTP 超时；
 * 一旦 Quartz 没配，通知就永久躺在表里。</p>
 *
 * <p>本任务把投递彻底移出请求线程，失败按指数退避重试，
 * 超过 {@code MAX_RETRY_TIMES} 由 mapper 的单条 UPDATE 自动置 {@code GIVEUP} 等人工介入。
 * <b>外部 HTTP 触发只是快速路径，NEVER 因为有了它就把本任务停掉</b>。</p>
 *
 * <p><b>多副本</b>：无分布式锁。两个副本可能同时捞到同一条并各发一次，
 * 因此<b>APP 侧 MUST 按 orderNo 幂等</b>；{@code markSuccess} 的前置状态白名单保证
 * 只有一个副本能把它标成功，另一个拿到 0 行。这个取舍与 {@code F2fOrderExpireJob} 一致。</p>
 */
@Component
public class F2fNotifyJob {

    private final F2fNotifyDeliverer deliverer;

    private final int batchLimit;

    public F2fNotifyJob(F2fNotifyDeliverer deliverer,
                        @Value("${f2f.notify.scanLimit:100}") int batchLimit) {
        this.deliverer = deliverer;
        this.batchLimit = batchLimit;
    }

    @Scheduled(fixedDelayString = "${f2f.notify.scanIntervalMs:30000}",
            initialDelayString = "${f2f.notify.scanInitialDelayMs:20000}")
    public void deliverDueTasks() {
        deliverer.deliverDue(batchLimit);
    }
}
