package com.chinasofti.huateng.gatetxnpay.controller.internal;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
import com.chinasofti.huateng.gatetxnpay.service.impl.CrossDomainDivergenceService;
import com.chinasofti.huateng.gatetxnpay.service.impl.DebitRetryProcessor;
import com.chinasofti.huateng.gatetxnpay.service.impl.MetroTransferPushTaskProcessor;
import com.chinasofti.huateng.gatetxnpay.service.impl.OneSideOrderProcessor;
import com.chinasofti.huateng.gatetxnpay.service.impl.OfflineFareRecoveryProcessor;
import com.chinasofti.huateng.gatetxnpay.service.impl.RetryQueueConsumer;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/gate-txn-pay")
public class CompensationInternalController {

    private final OfflineFareRecoveryProcessor offlineFareRecoveryProcessor;
    private final MetroTransferPushTaskProcessor metroTransferPushTaskProcessor;
    private final DebitRetryProcessor debitRetryProcessor;
    private final GateTxnPayService gateTxnPayService;
    private final RetryQueueConsumer retryQueueConsumer;
    private final OneSideOrderProcessor oneSideOrderProcessor;
    private final CrossDomainDivergenceService crossDomainDivergenceService;

    public CompensationInternalController(OfflineFareRecoveryProcessor offlineFareRecoveryProcessor,
                                          MetroTransferPushTaskProcessor metroTransferPushTaskProcessor,
                                          DebitRetryProcessor debitRetryProcessor,
                                          GateTxnPayService gateTxnPayService,
                                          RetryQueueConsumer retryQueueConsumer,
                                          OneSideOrderProcessor oneSideOrderProcessor,
                                          CrossDomainDivergenceService crossDomainDivergenceService) {
        this.offlineFareRecoveryProcessor = offlineFareRecoveryProcessor;
        this.metroTransferPushTaskProcessor = metroTransferPushTaskProcessor;
        this.debitRetryProcessor = debitRetryProcessor;
        this.gateTxnPayService = gateTxnPayService;
        this.retryQueueConsumer = retryQueueConsumer;
        this.oneSideOrderProcessor = oneSideOrderProcessor;
        this.crossDomainDivergenceService = crossDomainDivergenceService;
    }


    @PostMapping("/debit/retry/default")
    public CommonResult retryDefaultChannelDebits() {
        return describe("行程扣费重试(非支付宝)", debitRetryProcessor.retryDefaultChannelDebits());
    }


    @PostMapping("/debit/retry/alipay")
    public CommonResult retryAlipayChannelDebits() {
        return describe("支付宝出行重试扣费", debitRetryProcessor.retryAlipayChannelDebits());
    }


    @PostMapping("/debit/retry/recent")
    public CommonResult retryRecentUnpaidDebits() {
        return describe("补站扣费周期查询更新", debitRetryProcessor.retryRecentUnpaidDebits());
    }

    @PostMapping("/offline-fare/recover")
    public CommonResult recoverOfflineFare() {
        return describe("离线码金额补偿", offlineFareRecoveryProcessor.recoverOfflineFarePendingOrders());
    }


    @PostMapping("/retry-queue/consume")
    public CommonResult consumeRetryQueue() {
        int consumed = retryQueueConsumer.consumeBatch();
        return describe("用户主动重试扣费队列消费", consumed);
    }


    @PostMapping("/oneside/process")
    public CommonResult processOneSideOrders() {
        int processed = oneSideOrderProcessor.processOneSideOrders();
        return describe("单边订单处理", processed);
    }

    @PostMapping("/metro-transfer/push")
    public CommonResult pushMetroTransfer() {
        return describe("公交换乘推送", metroTransferPushTaskProcessor.processReadyTasks());
    }


    @PostMapping("/debit/converge")
    public GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(
            @RequestBody GateTxnPayDebitConvergeReqDTO request) {
        return gateTxnPayService.convergeDebitStatusForSupplement(request);
    }


    @PostMapping("/divergenceCheck")
    public CompensateNotifyRespDTO divergenceCheck() {
        return crossDomainDivergenceService.check();
    }

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
