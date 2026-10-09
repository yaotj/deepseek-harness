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

/**
 * 用户主动重试扣费队列表消费者（ADR-D169 方案 A）。
 *
 * <p><b>职责</b>：定时扫描 {@code GATE_RETRY_QUEUE} 表，批量消费并调 {@link PaySignInitiator#retryAndConverge}，
 * 控制速率避免冲击支付中心。
 *
 * <p><b>速率控制策略</b>：
 * <ul>
 *   <li>每轮最多消费 {@code batchSize} 笔（默认 10）；</li>
 *   <li>两轮间隔 {@code intervalSeconds} 秒（默认 60）；</li>
 *   <li>失败订单最多重试 {@code maxRetries} 次（默认 3），之后标记 FAILED 等待人工介入。</li>
 * </ul>
 *
 * <p><b>与定时补偿任务的关系</b>：
 * 本消费者与 {@link DebitRetryProcessor} 的 sys_job 220/255/345 <b>并行不替代</b>：
 * <ul>
 *   <li>本消费者只处理用户通过 APP 主动触发的重试请求；</li>
 *   <li>sys_job 220/255 处理系统级批量重试（按次数上限 + 退避窗口）；</li>
 *   <li>sys_job 345 处理近 10 分钟未扣费订单的快速轮询。</li>
 * </ul>
 *
 * <p><b>注意事项</b>：
 * <ul>
 *   <li>本类<b>不带 {@code @Scheduled}</b>（由 web-admin Quartz 统一管理定时任务）；</li>
 *   <li>消费逻辑在 {@link #consumeBatch()} 中，调用方可手动触发用于测试或补偿；</li>
 *   <li>每次消费用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 抢占行，防止多实例重复消费。</li>
 * </ul>
 */
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

    /**
     * 执行一轮队列消费。
     *
     * <p>扫描 PENDING/FAILED（未达最大重试次数）的订单，抢占后调支付中心发起扣款，
     * 根据结果更新队列状态和订单状态。
     *
     * @return 本轮真正发起的笔数
     */
    @Transactional(rollbackFor = Exception.class)
    public int consumeBatch() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        // 1. 超时恢复：把超过 N 分钟的 PENDING/PROCESSING 恢复为 PENDING
        int recovered = gateRetryQueueMapper.recoverTimeout(timeoutMinutes, now);
        if (recovered > 0) {
            log.info("队列超时恢复, 恢复={}笔, 超时={}分钟", recovered, timeoutMinutes);
        }

        // 2. 扫描待消费队列（锁定行防并发）
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
                // 3. 抢占：标记为 PROCESSING
                int claimed = gateRetryQueueMapper.markProcessing(item.getId(), now);
                if (claimed == 0) {
                    skipped++;
                    continue;
                }

                // 4. 查询订单快照
                GateTxnPay order = gateTxnPayMapper.selectByOrderNo(item.getOrderNo());
                if (order == null) {
                    log.warn("队列消费订单不存在, orderNo={}", item.getOrderNo());
                    gateRetryQueueMapper.markDone(item.getId(), now);
                    skipped++;
                    continue;
                }

                // 5. 发起扣款
                String landed = paySignInitiator.retryAndConverge(order);
                retried++;

                // 6. 更新队列状态
                if ("PROCESSING".equals(landed)) {
                    // 扣款已受理，等支付中心回调收敛终态
                    gateRetryQueueMapper.markDone(item.getId(), now);
                } else {
                    // 扣款失败（FAIL），标记失败
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
                // 异常时保持 PROCESSING，下一轮会超时恢复
            }
        }

        if (skipped > 0 || failed > 0) {
            log.info("队列消费逐笔统计, 候选={}, 已重发={}, 跳过={}, 异常={}",
                    items.size(), retried, skipped, failed);
        }

        return retried;
    }

    /**
     * 查询队列状态统计。
     */
    public List<GateRetryQueueMapper.QueueStat> getStatusStats() {
        return gateRetryQueueMapper.countByStatus();
    }
}
