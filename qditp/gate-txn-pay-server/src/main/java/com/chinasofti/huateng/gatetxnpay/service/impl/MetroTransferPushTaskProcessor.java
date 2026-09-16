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
 * <p><b>2.0.73 起本类不再有 {@code @Scheduled}，调度移到 web-admin 的 `sys_job`</b>
 * （`gateTxnPayQuartzTask.pushMetroTransfer()`，cron `0 0/1 * * * ?`），入口是
 * {@code POST /internal/gate-txn-pay/metro-transfer/push}。**NEVER 在这里加回 `@Scheduled`** ——
 * 两套调度源互不知情，同一条任务会被推两次。</p>
 *
 * <p><b>轮询周期由 10 秒变成 60 秒（用户 2026-09-15 裁决），这是外部可感知的行为变化</b>：
 * 公交卡系统那侧收到换乘推送的时延上限从 10 秒变为 60 秒。要调回更密 MUST 改 `sys_job` 的 cron，
 * **NEVER 改回模块内 `@Scheduled`**；同时注意 `SYS_JOB_LOG` 是「开始即入库」，cron 每打密一档
 * 那张表的日增行数就翻一档。</p>
 *
 * <p>调度语义从 `fixedDelay`（上一轮跑完再等）变成 cron（墙上时钟到点即触发），
 * 不重叠只靠 `sys_job.concurrent='1'` 保证，**那一列 NEVER 改成 '0'**。</p>
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
                                          @Value("${wallet.metro-transfer-enabled:false}") boolean enabled,
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

    /**
     * 跑一轮投递，返回本轮**捞到并逐条处理过**的任务数（不等于成功数，成败由 {@link #processOne} 各自落库）。
     *
     * <p>{@code enabled=false} 返回 -1、扫表异常返回 -2 —— **用负值区分「开关关了」「扫表炸了」
     * 与「本轮没任务（0）」**，三种情况在调度日志里长得一样时排查「推送为什么不动」会卡住。</p>
     */
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

    /**
     * 处理单条任务。**整个方法体 MUST 兜住全部异常**：抛出去会终止 for、本轮剩余任务全部不处理。
     *
     * <p>刻意分成「投递」与「落状态」两段，两段的 catch 语义完全不同：</p>
     * <ul>
     *   <li>投递段的异常 = 没拿到对端答复 ⇒ 归 {@link RpcOutcome.Unreachable}，可重试；</li>
     *   <li>落状态段的异常 **NEVER 再改判成重试**。改造前两段合在一个 try 里，
     *       {@code markSuccess} 抛异常会掉进同一个 catch 继续执行 {@code markRetry}，
     *       而那条 CAS 的前置 {@code STATUS='PROCESSING'} 此时仍然成立
     *       —— 于是**一笔已推送成功的任务被重新排入重推，对端收到重复行程**。
     *       现在这种情况让行留在 {@code PROCESSING}，交给 {@code recoverStuckProcessing}
     *       在租约（{@code wallet.metro-transfer-processing-lease-seconds}）到期后退回 {@code RETRY}。</li>
     * </ul>
     */
    private void processOne(MetroTransferPushTask task) {
        // 抢占段单独兜异常：抢不到（别的副本先到）或抢占本身报错，都只能跳过这条，
        // NEVER 归到下面的投递结论里 —— 没抢到就等于一个请求都没发，落任何状态都是错的。
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

    /**
     * 按三分支落状态。每条 mapper 的影响行数 **MUST 接住**：0 行意味着这行已被人工或其它副本改过，
     * 只能记日志，**NEVER 当成写成功**（{@code docs/domain/outbox.md} §二）。
     */
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
                // 对端答复了、且业务上拒绝：重推一万次也不会成功，MUST 一次即终态 + 人工。
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

    /**
     * 按出站订单判定要不要给公交侧推换乘，要推就构建任务；不满足条件返回 {@code null}。
     *
     * <p>放在本类而不是订单服务里，是为了让 {@code MetroTransferPushTask} 的**首尾在同一处**：
     * 这里决定「生不生」，下面 {@link #processReadyTasks} 决定「怎么送、送不成怎么办」。
     * 两个调用方（出站首次落单、离线码金额补偿）共用这一份，**NEVER 各自复制一份判定**——
     * 五个条件里后三个是**排除**语义，复制一次写反一个，表现是「少推」或「多推」，
     * 两边都不报错：少推乘客拿不到公交换乘优惠，多推给了不该给的（蓝牙 / 同行 / 第三方票）。</p>
     *
     * <p><b>2.0.77 起 {@code wallet.metro-transfer-enabled=false} 时连任务都不建</b>（用户
     * 2026-09-15 明确要求）。此前该开关只拦投递、不拦生成，于是关闭期间行程仍逐条落 {@code PENDING}
     * 堆在表里。那样做有个**看着像优点、实际是缺陷**的后果：一旦开关打开，**几天前的陈旧行程会一次性
     * 涌向公交卡系统**，而换乘优惠是有时效的业务语义，补推一批过期行程比不推更糟；`batch-size=50`
     * 还会让积压按分钟慢慢吐、期间对端被持续打。现在语义收敛成一句：**这个开关等于「本功能是否启用」**。</p>
     *
     * <p><b>关闭期间的数据并没有不可恢复</b>——这是本改动敢做的前提。任务表六个业务列
     * （{@code THIRD_USER_ID} / {@code TRANS_DATE} / {@code TRANS_TIME} / {@code PAY_CHANNEL_TYPE} /
     * {@code TRANSFER_FLAG} / {@code CARD_TYPE}）**全部取自 {@code GATE_TXN_PAY} 同一行**，
     * 没有一个字段是这里凭空生成的，因此任何时候都能用 `INSERT ... SELECT` 按下面 `shouldPush` 的
     * 同四个条件把某个时间窗的任务补建回来。**改动这里的字段映射时 MUST 同步核对那条补建 SQL 的口径**，
     * 否则「补建」会悄悄产出与实时链路不一样的任务。</p>
     *
     * <p><b>NEVER 把这个开关判断挪到两个调用方里</b>：那等于把「本功能是否启用」这一个决定复制成两份，
     * 与上面「NEVER 各自复制一份判定」是同一个理由。</p>
     */
    public MetroTransferPushTask buildMetroTransferPushTask(GateTxnPay order) {
        if (order == null || !shouldPushMetroTransfer(order)) {
            return null;
        }
        // 开关判断 MUST 放在 shouldPush 之后：放前面的话每笔非钱包出站都要打一行日志，
        // 而那些单本来就不推、与开关无关，纯噪音。放这里则「本该推、被开关拦下」才留痕。
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
        // 五个条件：出站 / 钱包渠道 / 非蓝牙 / 非同行 / 非第三方。
        // 三个字段级判定收口在 GateTxnPayFieldCode，与 FareCalculator、GateTxnPayServiceImpl 共用同一份，
        // NEVER 在这里复制字面量（加新的出站码或钱包码时会漏掉这一处）。
        return GateTxnPayFieldCode.isExitTrxType(order.getTrxType())
                && GateTxnPayFieldCode.isWalletVendor(order.getPaymentVendor())
                && !GateTxnPayFieldCode.isBluetoothChannel(order.getChannelType())
                && !GateTxnPayFieldCode.isCompanionOrThirdParty(order.getCompanionFlag());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
