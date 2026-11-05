package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.service.F2fReportRecovery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class F2fReportRecoveryJob {

    private final F2fReportRecovery recovery;

    private final int batchLimit;

    private final long staleSeconds;

    public F2fReportRecoveryJob(F2fReportRecovery recovery,
                               @Value("${f2f.report.scanLimit:100}") int batchLimit,
                               @Value("${f2f.report.staleSeconds:300}") long staleSeconds) {
        this.recovery = recovery;
        this.batchLimit = batchLimit;
        this.staleSeconds = staleSeconds;
    }

    @Scheduled(fixedDelayString = "${f2f.report.scanIntervalMs:120000}",
            initialDelayString = "${f2f.report.scanInitialDelayMs:60000}")
    public void resumeDueReports() {
        recovery.resumeDue(batchLimit, staleSeconds);
    }
}
