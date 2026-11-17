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


@Service
public class DailyTicketExpireService {

    private static final Logger log = LoggerFactory.getLogger(DailyTicketExpireService.class);

    private final DailyTicketInstanceMapper instanceMapper;

    private final TravelParentSummaryWriter travelParentSummaryWriter;

    private final int batchLimit;


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
