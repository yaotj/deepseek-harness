package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayFieldCode;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import com.chinasofti.huateng.gatetxnpay.mapper.MetroTransferPushTaskMapper;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公交推送 outbox 的消费入口，任务抢占依赖条件更新，支持多实例并发运行。
 *
 * <p>NEVER 加回 {@code @Scheduled}，已改由 web-admin {@code sys_job} 300 触发（2026-09-21 由 121 改号为 300，ADR-D80）。
 */
@Service
public class MetroTransferPushTaskProcessor {
    private static final Logger log = LoggerFactory.getLogger(MetroTransferPushTaskProcessor.class);
    private static final int LAST_ERROR_MAX_LENGTH = 512;
    private final MetroTransferPushTaskMapper taskMapper;
    private final MetroTransferPushClient client;
    private final boolean enabled;
    private final int batchSize;
    private final int maxRetry;
    private final long processingLeaseSeconds;

    public MetroTransferPushTaskProcessor(MetroTransferPushTaskMapper taskMapper, MetroTransferPushClient client,
                                          @Value("${wallet.metro-transfer-enabled:true}") boolean enabled,
                                          @Value("${wallet.metro-transfer-batch-size:50}") int batchSize,
                                          @Value("${wallet.metro-transfer-max-retry:10}") int maxRetry,
                                          @Value("${wallet.metro-transfer-processing-lease-seconds:120}") long processingLeaseSeconds) {
        this.taskMapper = taskMapper;
        this.client = client;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxRetry = maxRetry;
        this.processingLeaseSeconds = processingLeaseSeconds;
    }

    /** 跑一轮投递，返回本轮捞到并逐条处理过的任务数（不等于成功数，成败由 {@link #processOne} 各自落库）。 */
    public int processReadyTasks() {
        if (!enabled) {
            log.info("公交换乘推送开关未开启（wallet.metro-transfer-enabled=false），本轮跳过");
            return -1;
        }
        List<MetroTransferPushTask> tasks;
        try {
            int recovered = taskMapper.recoverStuckProcessing(processingLeaseSeconds);
            if (recovered > 0) {
                log.warn("已恢复租约超时的公交推送任务, count={}", recovered);
            }
            tasks = taskMapper.selectReady(batchSize);
        } catch (Exception e) {
            log.error("公交推送任务扫表失败，本轮跳过", e);
            return -2;
        }
        for (MetroTransferPushTask task : tasks) {
            processOne(task);
        }
        return tasks.size();
    }

    /** 处理单条任务。 */
    private void processOne(MetroTransferPushTask task) {
        try {
            if (taskMapper.claim(task.getId()) != 1) {
                return;
            }
        } catch (Exception e) {
            log.error("公交推送任务抢占失败，跳过该条继续本批, id={}, orderNo={}", task.getId(), task.getOrderNo(), e);
            return;
        }
        RpcOutcome outcome;
        try {
            outcome = client.push(task);
        } catch (Exception e) {
            outcome = new RpcOutcome.Unreachable(e);
        }
        try {
            landOutcome(task, outcome);
        } catch (Exception e) {
            log.error("公交推送结果落库失败，该行留在 PROCESSING 等租约回收，NEVER 就此重推, id={}, orderNo={}",
                    task.getId(), task.getOrderNo(), e);
        }
    }

    /** 按三分支落状态。 */
    private void landOutcome(MetroTransferPushTask task, RpcOutcome outcome) {
        int retry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        switch (outcome) {
            case RpcOutcome.Ok ignored -> {
                int rows = taskMapper.markSuccess(task.getId());
                if (rows != 1) {
                    log.warn("公交推送已成功但状态未落（该行已被并发改动）, id={}, orderNo={}, rows={}",
                            task.getId(), task.getOrderNo(), rows);
                }
            }
            case RpcOutcome.BizRejected rejected -> {
                int rows = taskMapper.markFailed(task.getId(), retry,
                        truncate("对端业务拒绝:" + rejected.retCode() + "/" + rejected.retMsg()));
                log.error("公交换乘推送被对端业务拒绝，已置 FAILED 终态待人工核对（NEVER 重推）, "
                                + "id={}, orderNo={}, retCode={}, retMsg={}, rows={}",
                        task.getId(), task.getOrderNo(), rejected.retCode(), rejected.retMsg(), rows);
            }
            case RpcOutcome.Unreachable unreachable -> {
                if (retry >= maxRetry) {
                    int rows = taskMapper.markFailed(task.getId(), retry,
                            truncate("重试耗尽:" + causeOf(unreachable)));
                    log.error("公交换乘推送重试耗尽，已置 FAILED 终态待人工核对, id={}, orderNo={}, retry={}, rows={}",
                            task.getId(), task.getOrderNo(), retry, rows, unreachable.cause());
                    return;
                }
                long delaySeconds = Math.min(3600L, 1L << Math.min(retry, 10));
                int rows = taskMapper.markRetry(task.getId(), retry,
                        LocalDateTime.now().plusSeconds(delaySeconds), truncate(causeOf(unreachable)));
                log.warn("公交换乘推送未获答复，{}s 后重试, id={}, orderNo={}, retry={}/{}, rows={}",
                        delaySeconds, task.getId(), task.getOrderNo(), retry, maxRetry, rows, unreachable.cause());
            }
        }
    }

    private String causeOf(RpcOutcome.Unreachable unreachable) {
        Throwable cause = unreachable.cause();
        if (cause == null) {
            return "未知原因";
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** LAST_ERROR 列长上限，超长直接抛 ORA-12899。 */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > LAST_ERROR_MAX_LENGTH ? text.substring(0, LAST_ERROR_MAX_LENGTH) : text;
    }

    /** 按出站订单判定要不要给公交侧推换乘，要推就构建任务，不满足条件返回 {@code null}。 */
    public MetroTransferPushTask buildMetroTransferPushTask(GateTxnPay order) {
        if (order == null || !shouldPushMetroTransfer(order)) {
            return null;
        }
        if (!enabled) {
            log.info("公交换乘推送开关未开启（wallet.metro-transfer-enabled=false），本单不建推送任务, orderNo={}",
                    order.getOrderNo());
            return null;
        }
        MetroTransferPushTask task = new MetroTransferPushTask();
        task.setThirdUserId(order.getThirdUserId());
        task.setTransDate(order.getOutTime() != null && order.getOutTime().length() >= 8
                ? order.getOutTime().substring(0, 8) : order.getTxnDate());
        task.setTransTime(order.getOutTime() != null && order.getOutTime().length() >= 14
                ? order.getOutTime().substring(8, 14) : order.getOutTime());
        task.setPayChannelType(order.getPaymentVendor());
        task.setTransferFlag(order.getTransferFlag());
        task.setCardType(order.getCardType());
        return task;
    }

    private boolean shouldPushMetroTransfer(GateTxnPay order) {
        return GateTxnPayFieldCode.isExitTrxType(order.getTrxType())
                && GateTxnPayFieldCode.isWalletVendor(order.getPaymentVendor())
                && !GateTxnPayFieldCode.isBluetoothChannel(order.getChannelType())
                && !GateTxnPayFieldCode.isCompanionOrThirdParty(order.getCompanionFlag());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
