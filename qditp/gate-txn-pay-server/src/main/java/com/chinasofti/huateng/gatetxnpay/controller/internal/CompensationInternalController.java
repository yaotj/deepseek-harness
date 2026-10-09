package com.chinasofti.huateng.gatetxnpay.controller.internal;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
import com.chinasofti.huateng.gatetxnpay.service.impl.DebitRetryProcessor;
import com.chinasofti.huateng.gatetxnpay.service.impl.MetroTransferPushTaskProcessor;
import com.chinasofti.huateng.gatetxnpay.service.impl.OfflineFareRecoveryProcessor;
import com.chinasofti.huateng.gatetxnpay.service.impl.RetryQueueConsumer;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本模块补偿链路的外部调度入口（2.0.73 新增，现有 6 个端点，全部由 web-admin 的 `sys_job` 触发）。 */
@RestController
@RequestMapping("/internal/gate-txn-pay")
public class CompensationInternalController {

    private final OfflineFareRecoveryProcessor offlineFareRecoveryProcessor;
    private final MetroTransferPushTaskProcessor metroTransferPushTaskProcessor;
    /** 甲方需求「行程扣费重试」（220 / 255）与「补站扣费周期查询更新」（235）与「用户主动重试扣费队列消费」（400）共用。 */
    private final DebitRetryProcessor debitRetryProcessor;
    /** 补款收敛用（2026-09-16 新增的第三个端点）。 */
    private final GateTxnPayService gateTxnPayService;
    /** ADR-D169 方案 A：用户主动重试扣费队列消费。 */
    private final RetryQueueConsumer retryQueueConsumer;

    public CompensationInternalController(OfflineFareRecoveryProcessor offlineFareRecoveryProcessor,
                                          MetroTransferPushTaskProcessor metroTransferPushTaskProcessor,
                                          DebitRetryProcessor debitRetryProcessor,
                                          GateTxnPayService gateTxnPayService,
                                          RetryQueueConsumer retryQueueConsumer) {
        this.offlineFareRecoveryProcessor = offlineFareRecoveryProcessor;
        this.metroTransferPushTaskProcessor = metroTransferPushTaskProcessor;
        this.debitRetryProcessor = debitRetryProcessor;
        this.gateTxnPayService = gateTxnPayService;
        this.retryQueueConsumer = retryQueueConsumer;
    }

    /**
     * 跑一轮**非支付宝渠道**的行程扣费批量重试（甲方需求「行程扣费重试」）。对应 {@code sys_job} 220。
     *
     * <p>含终态 {@code FAIL}（业主裁决）；防重扣靠 {@code DEBIT_RETRY_TIMES} 上限与回溯窗口，
     * 两者都在 {@code DebitRetryProcessor} 里可配，**NEVER 把上限调成 0 或窗口开到无限**。
     */
    @PostMapping("/debit/retry/default")
    public CommonResult retryDefaultChannelDebits() {
        return describe("行程扣费重试(非支付宝)", debitRetryProcessor.retryDefaultChannelDebits());
    }

    /**
     * 跑一轮**支付宝出行渠道**的行程扣费批量重试。对应 {@code sys_job} 255。
     *
     * <p>与上面那个端点的差别只在扫表的渠道谓词（{@code ISSUE_CHANNEL_CODE='07'}），但**扣费出口完全不同** ——
     * 支付宝那支走 alipay-pay-sign、压根不经过 pay-sign 与支付中心（见 {@code PaySignInitiator#converge}）。
     * **NEVER 把两个端点合回一个**，那会让一侧渠道故障连带拖住另一侧。
     */
    @PostMapping("/debit/retry/alipay")
    public CommonResult retryAlipayChannelDebits() {
        return describe("支付宝出行重试扣费", debitRetryProcessor.retryAlipayChannelDebits());
    }

    /**
     * 跑一轮**近 N 分钟未扣费订单**的重试（甲方需求「补站扣费周期查询更新」）。对应 {@code sys_job} 345（2026-09-21 编号先后为 135、265、235、345），每分钟一轮。
     *
     * <p>与上面两个端点**并行、不可相互替代**：本条不分渠道、多捞 {@code INIT}（落单后扣费压根没发起的那种）、
     * 只看 {@code CREATE_TIME} 落在近 N 分钟内的单，且**不写 `DEBIT_RETRY_TIMES` / `DEBIT_NEXT_RETRY_TIME`**
     * 两个记账列（业主裁决「不设退避」，见 ADR-D154）。判据与已知代价写在
     * {@code DebitRetryProcessor#retryRecentUnpaidDebits()} 的 Javadoc 里，**改口径前 MUST 先读它**。
     */
    @PostMapping("/debit/retry/recent")
    public CommonResult retryRecentUnpaidDebits() {
        return describe("补站扣费周期查询更新", debitRetryProcessor.retryRecentUnpaidDebits());
    }

    /** 跑一轮离线码金额补偿（原 `OfflineFareRecoveryProcessor` 的 `@Scheduled`）。 */
    @PostMapping("/offline-fare/recover")
    public CommonResult recoverOfflineFare() {
        return describe("离线码金额补偿", offlineFareRecoveryProcessor.recoverOfflineFarePendingOrders());
    }

    /**
     * ADR-D169 方案 A：消费用户主动重试扣费队列表。对应 {@code sys_job} 400，每 5 分钟一轮。
     *
     * <p>用户通过 APP 触发 `/app/payment/requestPayFailOrder` 后，订单进入 {@code GATE_RETRY_QUEUE} 表
     * （状态 `PENDING`），本任务扫描并调 {@code PaySignInitiator.retryAndConverge} 发起扣款。
     * 速率控制：每轮最多消费 10 笔（`batchSize`），失败最多重试 3 次（`maxRetries`），
     * 超时 24 小时自动恢复（`timeoutMinutes`）。与 sys_job 220/255/345 并行不替代。
     */
    @PostMapping("/retry-queue/consume")
    public CommonResult consumeRetryQueue() {
        int consumed = retryQueueConsumer.consumeBatch();
        return describe("用户主动重试扣费队列消费", consumed);
    }

    /** 跑一轮公交换乘推送（原 `MetroTransferPushTaskProcessor` 的 `@Scheduled`，周期 10s → 60s）。 */
    @PostMapping("/metro-transfer/push")
    public CommonResult pushMetroTransfer() {
        return describe("公交换乘推送", metroTransferPushTaskProcessor.processReadyTasks());
    }

    /**
     * 在线补款支付成功后收敛原行程的扣费状态（供 face-pay-server 调用，2026-09-16 新增）。
     *
     * <p>该端点能把任意订单号的 {@code DEBIT_STATUS} 直接改成 {@code SUCCESS}（等于免单），
     * 与另两个端点语义相反：NEVER 恒返 {@code 0000}、NEVER 吞异常。
     */
    @PostMapping("/debit/converge")
    public GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(
            @RequestBody GateTxnPayDebitConvergeReqDTO request) {
        return gateTxnPayService.convergeDebitStatusForSupplement(request);
    }

    /** 把 Processor 的返回值翻成人能读的 `retMsg`。 */
    private CommonResult describe(String taskName, int processed) {
        CommonResult result = new CommonResult();
        result.setRetCode("0000");
        result.setRetMsg(switch (processed) {
            case -1 -> taskName + "开关未开启，本轮跳过";
            case -2 -> taskName + "本轮扫表异常，已记日志，等下一轮重入";
            default -> taskName + "本轮处理 " + processed + " 笔";
        });
        return result;
    }
}
