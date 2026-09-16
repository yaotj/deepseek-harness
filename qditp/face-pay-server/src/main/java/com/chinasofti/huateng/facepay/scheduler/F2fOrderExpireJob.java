package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.service.F2fOrderExpireService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 二维码过期订单收口任务。<b>方向 1 防重下单的兜底就落在这里</b>——重复下单产生的多余订单靠它
 * 从 {@code CREATED} / {@code PAYING} 收敛掉，没有这个任务，设计文档 §十六 的风险边界不成立。
 *
 * <h2>三条约束</h2>
 * <ol>
 *   <li><b>不带 {@code @Transactional}</b>：每笔收口都要调支付中心，事务包住网络调用是 2026-08-26
 *       生产事故的成因（AGENTS.md §5.2）。</li>
 *   <li><b>逐笔 try/catch</b>：一笔异常不能让整批停摆，否则一条脏数据会永久堵住收口。</li>
 *   <li><b>重试 MUST 有出口</b>：扫表带放弃窗口下界（{@code f2f.order.expireGiveUpHours}），
 *       超窗的单不再被扫、不再外呼，只由 {@code countStaleExpiredOrders} 计数告警。
 *       <b>NEVER 让「状态不明就下轮再试」成为无上限循环</b>——2026-09-10 实测：订单
 *       {@code F200202609100914540082} 每 30 秒查一次支付中心、回 {@code 9999 未找到数据}，
 *       持续 40 分钟没有出口，与 {@code F2fRefundReconcileJob} 的 {@code RETRY_TIMES < 20}
 *       形成的对比正是这个缺陷被发现的方式。</li>
 * </ol>
 *
 * <p><b>多副本说明</b>：本模块用 {@code @Scheduled}（无分布式锁，项目不引 Redis）。多副本会重复扫同
 * 一批，但每个动作都是幂等的——{@code updateStatus} 带前置状态白名单、{@code markPaid} 靠
 * {@code UK_F2F_PAY_SUCCESS} 与状态白名单收敛，重复执行只是多几条 UPDATE 命中 0 行。
 * 代价是对支付中心的重复查询，因此<b>副本数变化时 MUST 复核扫表间隔与批量</b>。</p>
 */
@Component
public class F2fOrderExpireJob {

    private static final Logger log = LoggerFactory.getLogger(F2fOrderExpireJob.class);

    /**
     * 收口逻辑的宿主。2026-09-16 从 {@code F2fTvmOrderService} 拆出（P2），
     * 三个方法名与语义一行未改。
     */
    private final F2fOrderExpireService expireService;

    private final int batchLimit;

    public F2fOrderExpireJob(F2fOrderExpireService expireService,
                             @Value("${f2f.order.expireScanLimit:200}") int batchLimit) {
        this.expireService = expireService;
        this.batchLimit = batchLimit;
    }

    @Scheduled(fixedDelayString = "${f2f.order.expireScanIntervalMs:30000}",
            initialDelayString = "${f2f.order.expireScanInitialDelayMs:15000}")
    public void reconcileExpiredOrders() {
        List<F2fOrder> candidates;
        try {
            candidates = expireService.loadExpiredCandidates(batchLimit);
        } catch (RuntimeException e) {
            log.error("过期订单扫表失败", e);
            return;
        }
        if (candidates.isEmpty()) {
            return;
        }
        int resolved = 0;
        int pending = 0;
        int failed = 0;
        for (F2fOrder order : candidates) {
            try {
                if (expireService.reconcileExpiredOrder(order)) {
                    resolved++;
                } else {
                    pending++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("过期订单收口异常, orderNo={}", order.getOrderNo(), e);
            }
        }
        log.info("过期订单收口完成, 候选={}, 已收口={}, 待重试={}, 异常={}",
                candidates.size(), resolved, pending, failed);
        warnStaleOrders();
    }

    /**
     * 已过放弃窗口、不再自动收口的单只在这里告警一次。
     *
     * <p>本方法自身的异常吞掉——告警失败 NEVER 影响收口主流程。</p>
     */
    private void warnStaleOrders() {
        try {
            long stale = expireService.countStaleExpiredOrders();
            if (stale > 0) {
                log.error("有 {} 笔过期订单已超放弃窗口仍停在 CREATED/PAYING，不再自动收口，需人工判定", stale);
            }
        } catch (RuntimeException e) {
            log.warn("统计超窗未收口订单失败", e);
        }
    }
}
