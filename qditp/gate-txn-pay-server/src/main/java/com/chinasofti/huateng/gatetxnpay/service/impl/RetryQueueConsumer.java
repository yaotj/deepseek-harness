package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.entity.GateRetryQueue;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateRetryQueueMapper;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.paysign.PaySignInitiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;


@Service
public class RetryQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(RetryQueueConsumer.class);

    private final GateRetryQueueMapper gateRetryQueueMapper;
    private final GateTxnPayMapper gateTxnPayMapper;
    private final PaySignInitiator paySignInitiator;
    private final int batchSize;
    private final int maxRetries;
    private final int timeoutMinutes;

    public RetryQueueConsumer(GateRetryQueueMapper gateRetryQueueMapper,
                              GateTxnPayMapper gateTxnPayMapper,
                              PaySignInitiator paySignInitiator,
                              @Value("${gate.debitRetry.queue.batchSize:10}") int batchSize,
                              @Value("${gate.debitRetry.queue.maxRetries:3}") int maxRetries,
                              @Value("${gate.debitRetry.queue.timeoutMinutes:1440}") int timeoutMinutes) {
        this.gateRetryQueueMapper = gateRetryQueueMapper;
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.paySignInitiator = paySignInitiator;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.timeoutMinutes = timeoutMinutes;
    }


    @Transactional(rollbackFor = Exception.class)
    public int consumeBatch() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        int recovered = gateRetryQueueMapper.recoverTimeout(timeoutMinutes, now);
        if (recovered > 0) {
            log.info("队列超时恢复, 恢复={}笔, 超时={}分钟", recovered, timeoutMinutes);
        }

        List<GateRetryQueue> items;
        try {
            items = gateRetryQueueMapper.selectPendingForConsume(batchSize, now);
        } catch (RuntimeException e) {
            log.error("队列消费扫描失败", e);
            return 0;
        }

        if (items.isEmpty()) {
            return 0;
        }

        int retried = 0;
        int skipped = 0;
        int failed = 0;

        for (GateRetryQueue item : items) {
            try {
                int claimed = gateRetryQueueMapper.markProcessing(item.getId(), now);
                if (claimed == 0) {
                    skipped++;
                    continue;
                }

                GateTxnPay order = gateTxnPayMapper.selectByOrderNo(item.getOrderNo());
                if (order == null) {
                    log.warn("队列消费订单不存在, orderNo={}", item.getOrderNo());
                    gateRetryQueueMapper.markDone(item.getId(), now);
                    skipped++;
                    continue;
                }

                String landed = paySignInitiator.retryAndConverge(order);
                retried++;

                if ("PROCESSING".equals(landed)) {
                    gateRetryQueueMapper.markDone(item.getId(), now);
                } else {
                    int retryCount = (item.getRetryCount() != null ? item.getRetryCount() : 0) + 1;
                    gateRetryQueueMapper.markFailed(item.getId(), now, retryCount,
                            "扣款失败: " + landed);
                    if (retryCount >= maxRetries) {
                        log.warn("队列消费已达最大重试次数, orderNo={}, retryCount={}",
                                item.getOrderNo(), retryCount);
                    }
                }

            } catch (RuntimeException e) {
                failed++;
                log.error("队列消费异常, queueId={}, orderNo={}", item.getId(), item.getOrderNo(), e);
            }
        }

        if (skipped > 0 || failed > 0) {
            log.info("队列消费逐笔统计, 候选={}, 已重发={}, 跳过={}, 异常={}",
                    items.size(), retried, skipped, failed);
        }

        return retried;
    }


    public List<GateRetryQueueMapper.QueueStat> getStatusStats() {
        return gateRetryQueueMapper.countByStatus();
    }
}
