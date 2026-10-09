package com.chinasofti.huateng.dailyticket.service.expire;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketInstanceStatus;
import com.chinasofti.huateng.dailyticket.service.travel.TravelParentSummaryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 日票有效期过期收敛：把「有效期已过、状态还停在 ACTIVATED / USED」的票推进成 EXPIRED。
 *
 * <p>为什么需要它：在此之前本模块**只在查询时动态判过期**
 * （{@code DailyTicketInstanceLifecycleService.validateEntryCheck} / {@code checkRideAvailability}
 * 拿 {@code System.currentTimeMillis()} 和 COUNTING_END 比），从不回写状态。于是
 * {@code TICKET_STATUS} 与事实长期不一致：一张早已过期的票在库里仍是 ACTIVATED，
 * 运营后台、对账口径、旅游票主单汇总看到的都是「可用」。本类只做状态收敛，
 * **NEVER 顺手去掉那两处动态判断** —— 它们是过闸拦截的最后一道闸，收敛任务有延迟（按 cron 跑）。
 *
 * <p>EXPIRED 这个取值原先只有一个写入点：计次票次数扣到 0（{@code markUsed} 里
 * {@code remainTimes == 0}）。本类是第二个写入点，语义是「日期过期」。两者共用同一个状态值
 * 是**有意的**（业主未要求区分，且下游对两者的处置一致：不可过闸、不可退款），
 * 要区分就得新增状态值并同批改 selectForEntryCheck 白名单与退款侧判断。
 *
 * <p><b>已知业务影响，上线前 MUST 与业主确认</b>：
 * {@code DailyTicketRefundInitiationService} 对非 ACTIVATED 一律返「车票已使用，不允许退款」，
 * 因此被本任务收敛成 EXPIRED 的票**从此不能再发起退款**。这是「过期票不应再退」的自然结果，
 * 但它把一个此前事实上存在的口子关掉了，属行为变更。
 *
 * <p>本类刻意不带 {@code @Transactional}：口径同本模块其余服务类（见
 * {@code DailyTicketInstanceLifecycleService} 的类注释）。逐条 CAS 各自自动提交，
 * 单条失败不影响同批其余票，也不会把一整批扫描结果锁在一个长事务里。
 */
@Service
public class DailyTicketExpireService {

    private static final Logger log = LoggerFactory.getLogger(DailyTicketExpireService.class);

    private final DailyTicketInstanceMapper instanceMapper;

    private final TravelParentSummaryWriter travelParentSummaryWriter;

    /** 单轮最多处理多少张，防止一次扫描把库里全部历史票拉进内存。 */
    private final int batchLimit;

    /**
     * COUNTING_END 为空时是否回退按 ACTIVATE_TIME + PERIOD 天判过期。
     * 默认开：关掉后「激活但从未乘车」的票永远不会被收敛（那批票 COUNTING_END 恒为空）。
     */
    private final boolean fallbackByPeriod;

    public DailyTicketExpireService(
            DailyTicketInstanceMapper instanceMapper,
            TravelParentSummaryWriter travelParentSummaryWriter,
            @Value("${daily-ticket.expire.batch-limit:500}") int batchLimit,
            @Value("${daily-ticket.expire.fallback-by-period:true}") boolean fallbackByPeriod) {
        this.instanceMapper = instanceMapper;
        this.travelParentSummaryWriter = travelParentSummaryWriter;
        this.batchLimit = batchLimit;
        this.fallbackByPeriod = fallbackByPeriod;
    }

    /**
     * 扫一轮并收敛。
     *
     * @param scanned 本轮捞到的候选张数，{@code expired} 实际改成 EXPIRED 的张数，
     *                {@code skipped} CAS 影响 0 行的张数（票在本轮扫描后被并发改成了别的状态，
     *                最常见是退款锁票；属正常竞态，不是失败），{@code failed} 抛异常的张数
     */
    public ExpireResult convergeExpired() {
        Date now = new Date();
        List<DailyTicketInstance> candidates =
                instanceMapper.selectExpiredCandidates(now.getTime(), now, fallbackByPeriod, batchLimit);
        if (candidates == null || candidates.isEmpty()) {
            log.info("日票过期收敛：无候选, fallbackByPeriod={}, batchLimit={}", fallbackByPeriod, batchLimit);
            return new ExpireResult(0, 0, 0, 0);
        }

        int expired = 0;
        int skipped = 0;
        int failed = 0;
        for (DailyTicketInstance candidate : candidates) {
            try {
                int updated = instanceMapper.updateStatusIfCurrent(candidate.getId(),
                        candidate.getTicketStatus(), DailyTicketInstanceStatus.EXPIRED, now);
                if (updated == 0) {
                    skipped++;
                    log.info("日票过期收敛：CAS 影响 0 行，票状态已被并发改动, instanceId={}, orderNo={}, 扫描时状态={}",
                            candidate.getId(), candidate.getOrderNo(), candidate.getTicketStatus());
                    continue;
                }
                expired++;
                log.info("日票过期收敛：已置过期, instanceId={}, orderNo={}, cardNum={}, 原状态={}, "
                                + "countingEnd={}, activateTime={}, period={}",
                        candidate.getId(), candidate.getOrderNo(), candidate.getCardNum(),
                        candidate.getTicketStatus(), candidate.getCountingEnd(),
                        candidate.getActivateTime(), candidate.getPeriod());
                refreshTravelParent(candidate);
            } catch (Exception e) {
                failed++;
                log.error("日票过期收敛：单张处理异常, instanceId={}, orderNo={}",
                        candidate.getId(), candidate.getOrderNo(), e);
            }
        }
        log.info("日票过期收敛完成: 候选={}, 已过期={}, 跳过={}, 异常={}, fallbackByPeriod={}",
                candidates.size(), expired, skipped, failed, fallbackByPeriod);
        return new ExpireResult(candidates.size(), expired, skipped, failed);
    }

    /**
     * 旅游票主单汇总按子单状态重算（主单把 EXPIRED 与 USED 等价计数），
     * 失败只记日志：主单汇总是派生视图，重算不成不该让本张票的收敛回滚。
     */
    private void refreshTravelParent(DailyTicketInstance candidate) {
        try {
            travelParentSummaryWriter.refreshBySubOrder(candidate.getOrderNo());
        } catch (Exception e) {
            log.error("日票过期收敛：旅游票主单汇总刷新失败, orderNo={}", candidate.getOrderNo(), e);
        }
    }

    public record ExpireResult(int scanned, int expired, int skipped, int failed) {
    }
}
