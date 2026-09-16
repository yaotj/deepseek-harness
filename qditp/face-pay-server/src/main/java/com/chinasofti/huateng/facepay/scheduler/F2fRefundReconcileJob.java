package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.entity.F2fRefund;
import com.chinasofti.huateng.facepay.service.F2fRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款收口任务。<b>没有它，{@code F2fRefundService} 的「对端未答留 INIT」就是个死状态</b>——
 * 退款单会永远停在 {@code INIT}，钱到底退没退无人知晓。
 *
 * <h2>为什么必须主动查而不能只等回调</h2>
 * <p>与解约链路同理（AGENTS.md §8 末条）：退款回调只是快速路径，
 * 回调丢失、回调地址配错、我方短暂不可用都会让状态永久悬空。
 * 唯一可靠的收口方式是主动查支付中心的退款查询接口。</p>
 *
 * <h2>三条约束</h2>
 * <ol>
 *   <li><b>不带 {@code @Transactional}</b>：每笔都要调支付中心。</li>
 *   <li><b>逐笔 try/catch</b>：一笔异常不能让整批停摆。</li>
 *   <li><b>只捞 {@code NEXT_QUERY_TMS} 已到点的单</b>，退避没到点的本轮不动——
 *       既避免同一笔卡住的单被每分钟反复查，也避免刚落库的单立刻被扫走
 *       （此时支付中心那边可能还没记上）。</li>
 * </ol>
 *
 * <p><b>停止条件不在本类</b>：什么时候放弃自动收口由 {@code F2fRefundService} 按
 * {@code REQUEST_TMS} 的时间窗判定并置 MANUAL。本类 <b>NEVER 再传 maxRetryTimes</b>——
 * 按次数截断会让单静默掉出扫描范围、停在非终态无人管。</p>
 *
 * <p><b>多副本</b>：无分布式锁（项目不引 Redis）。重复扫同一批是安全的——
 * {@code updateStatus} 带前置状态白名单，第二个副本的 UPDATE 命中 0 行。
 * 代价是对支付中心的重复查询。</p>
 */
@Component
public class F2fRefundReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(F2fRefundReconcileJob.class);

    private final F2fRefundService refundService;

    private final int batchLimit;

    public F2fRefundReconcileJob(F2fRefundService refundService,
                                 @Value("${f2f.refund.scanLimit:100}") int batchLimit) {
        this.refundService = refundService;
        this.batchLimit = batchLimit;
    }

    @Scheduled(fixedDelayString = "${f2f.refund.scanIntervalMs:60000}",
            initialDelayString = "${f2f.refund.scanInitialDelayMs:30000}")
    public void reconcileRefunds() {
        List<F2fRefund> candidates;
        try {
            candidates = refundService.loadRetryCandidates(LocalDateTime.now(), batchLimit);
        } catch (RuntimeException e) {
            log.error("退款收口扫表失败", e);
            return;
        }
        if (candidates.isEmpty()) {
            return;
        }
        int resolved = 0;
        int pending = 0;
        int failed = 0;
        for (F2fRefund refund : candidates) {
            try {
                if (refundService.reconcileRefund(refund)) {
                    resolved++;
                } else {
                    pending++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("退款收口异常, refundNo={}", refund.getRefundNo(), e);
            }
        }
        log.info("退款收口完成, 候选={}, 已收口={}, 待重试={}, 异常={}",
                candidates.size(), resolved, pending, failed);
    }
}
