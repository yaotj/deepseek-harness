package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.service.F2fNotifyDeliverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
