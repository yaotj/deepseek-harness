package com.chinasofti.huateng.gatetxnpay.controller.internal;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
import com.chinasofti.huateng.gatetxnpay.service.impl.MetroTransferPushTaskProcessor;
import com.chinasofti.huateng.gatetxnpay.service.impl.OfflineFareRecoveryProcessor;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本模块两条补偿链路的外部调度入口（2.0.73 新增）。 */
@RestController
@RequestMapping("/internal/gate-txn-pay")
public class CompensationInternalController {

    private final OfflineFareRecoveryProcessor offlineFareRecoveryProcessor;
    private final MetroTransferPushTaskProcessor metroTransferPushTaskProcessor;
    /** 补款收敛用（2026-09-16 新增的第三个端点）。 */
    private final GateTxnPayService gateTxnPayService;

    public CompensationInternalController(OfflineFareRecoveryProcessor offlineFareRecoveryProcessor,
                                          MetroTransferPushTaskProcessor metroTransferPushTaskProcessor,
                                          GateTxnPayService gateTxnPayService) {
        this.offlineFareRecoveryProcessor = offlineFareRecoveryProcessor;
        this.metroTransferPushTaskProcessor = metroTransferPushTaskProcessor;
        this.gateTxnPayService = gateTxnPayService;
    }

    /** 跑一轮离线码金额补偿（原 `OfflineFareRecoveryProcessor` 的 `@Scheduled`）。 */
    @PostMapping("/offline-fare/recover")
    public CommonResult recoverOfflineFare() {
        return describe("离线码金额补偿", offlineFareRecoveryProcessor.recoverOfflineFarePendingOrders());
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
