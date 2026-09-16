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

/**
 * 本模块两条补偿链路的外部调度入口（2.0.73 新增）。
 *
 * <p>调度方是 web-admin 的 Quartz `sys_job`（`gateTxnPayQuartzTask.recoverOfflineFare()` /
 * `gateTxnPayQuartzTask.pushMetroTransfer()`，cron 均为 `0 0/1 * * * ?`），与 pay-sign-server 的
 * 7 个 `/internal/**` 端点、recon-server 的 `/internal/recon/daily/run` 同一形态。
 * 迁移前这两条链路各有一个模块内 `@Scheduled`，**NEVER 把 `@Scheduled` 加回去** ——
 * 两套调度源互不知情，离线码那条会并发发起扣款。</p>
 *
 * <p><b>【开发测试阶段：本接口当前无鉴权，上线前 MUST 恢复】</b>用户 2026-09-15 明确要求
 * 「不需要鉴权」。本模块没有 spring-security、没有全局拦截器兜底，因此现状等于允许任何网络可达方
 * 触发扫表级别的补偿批处理（其中离线码那条会**真的发起扣款**），与 AGENTS.md §5.2
 * 「新增状态变更型接口 MUST 有鉴权」相冲突，属**有意为之的临时降级**。</p>
 *
 * <p>恢复方式与同模块的 {@link ReconExportController} 相同 —— **注意那个类当前也没有鉴权**
 * （`X-Recon-Token` 已按用户 2026-09-11 的要求整段删除），两者是「同样待恢复」，
 * 不是「照着一份已有实现抄」。做法：引入共享令牌配置 + 请求头比对，
 * 比较 MUST 用 {@link java.security.MessageDigest#isEqual} 做定长时间比较，NEVER 用
 * {@code String.equals}（后者短路返回，可被逐字节计时探测出令牌内容）；令牌值由 K8s Secret 注入，
 * NEVER 在仓库里写默认真值。恢复时 MUST 同批给 `GateTxnPayClient.recoverOfflineFare` /
 * `pushMetroTransfer` 补上请求头 —— 这两个方法**已预留 {@code Map<String,String> headers} 形参**
 * （当前只用来传 traceId），加令牌不需要改签名。</p>
 *
 * <p>两个端点都**同步执行完一整轮才返回**，且都不抛异常（异常在 Processor 内兜住、以 -2 表达）。
 * 同步是刻意的：web-admin 侧 `sys_job.concurrent='1'`（禁止并发）靠的就是「上一次调用没返回就不发下一次」，
 * 这里改成异步受理即返回会让禁并发失效。两轮耗时都在秒级，远小于 `GateTxnPayClient` 的默认响应超时。</p>
 */
@RestController
@RequestMapping("/internal/gate-txn-pay")
public class CompensationInternalController {

    private final OfflineFareRecoveryProcessor offlineFareRecoveryProcessor;
    private final MetroTransferPushTaskProcessor metroTransferPushTaskProcessor;
    /**
     * 补款收敛用（2026-09-16 新增的第三个端点）。
     *
     * <p>与上面两个 Processor 不同：这条**不是补偿批处理**，而是由 face-pay-server 在
     * 单笔补款支付成功后同步调用的收敛入口，只是恰好共用本类的 `/internal/gate-txn-pay` 前缀。</p>
     */
    private final GateTxnPayService gateTxnPayService;

    public CompensationInternalController(OfflineFareRecoveryProcessor offlineFareRecoveryProcessor,
                                          MetroTransferPushTaskProcessor metroTransferPushTaskProcessor,
                                          GateTxnPayService gateTxnPayService) {
        this.offlineFareRecoveryProcessor = offlineFareRecoveryProcessor;
        this.metroTransferPushTaskProcessor = metroTransferPushTaskProcessor;
        this.gateTxnPayService = gateTxnPayService;
    }

    /**
     * 跑一轮离线码金额补偿（原 `OfflineFareRecoveryProcessor` 的 `@Scheduled`）。
     *
     * <p>**恒返 `0000`**：本轮扫表异常属可自愈（下一分钟再来一轮），返非 0 会让 `sys_job_log`
     * 记一次失败、淹没真正需要人看的失败。本轮结论在 `retMsg` 里，排查 MUST 看 `retMsg` 与模块日志。</p>
     */
    @PostMapping("/offline-fare/recover")
    public CommonResult recoverOfflineFare() {
        return describe("离线码金额补偿", offlineFareRecoveryProcessor.recoverOfflineFarePendingOrders());
    }

    /**
     * 跑一轮公交换乘推送（原 `MetroTransferPushTaskProcessor` 的 `@Scheduled`，周期 10s → 60s）。
     */
    @PostMapping("/metro-transfer/push")
    public CommonResult pushMetroTransfer() {
        return describe("公交换乘推送", metroTransferPushTaskProcessor.processReadyTasks());
    }

    /**
     * 在线补款支付成功后收敛原行程的扣费状态（供 face-pay-server 调用，2026-09-16 新增）。
     *
     * <p>本端点**与上面两个不同：NEVER 恒返 `0000`**。上面两个是「一轮扫表」，失败可自愈；
     * 这条是单笔资金收敛，调用方要靠 `retCode` + `converged` + `debitStatus` 三者分出
     * 「已结清 / 重复支付待退款 / 需人工核对」，把失败压成 `0000` 会让重复支付无声通过。</p>
     *
     * <p>本方法**不吞异常**：抛出去让调用方收到非 2xx，等价于 `RpcOutcome.Unreachable`，
     * 由 face-pay 的 `SupplementOrderCloseProcessor.converge`（cron `0 *&#47;5 * * * ?`）下一轮重入。
     * <b>NEVER 在这里 catch 后返一个假的业务码</b> —— 那会把「可重试」误判成「业务拒绝」。</p>
     *
     * <p>鉴权同本类其余端点：**当前无鉴权，上线前 MUST 一并恢复**（见类注释）。这条尤其要紧 ——
     * 它能把任意订单号的 `DEBIT_STATUS` 直接改成 `SUCCESS`，等于免单。</p>
     */
    @PostMapping("/debit/converge")
    public GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(
            @RequestBody GateTxnPayDebitConvergeReqDTO request) {
        return gateTxnPayService.convergeDebitStatusForSupplement(request);
    }

    /**
     * 把 Processor 的返回值翻成人能读的 `retMsg`。
     *
     * <p>-1 / -2 是两个 Processor 共用的约定（开关未开启 / 扫表异常），**改一处 MUST 改另一处**；
     * 这里 NEVER 把负值折叠成「本轮 0 笔」——那正是当初用负值区分的原因。</p>
     */
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
