package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.paysign.model.request.CheckFailedOrdersReqDTO;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;

/**
 * ITP 解约内部接口服务。
 */
public interface TerminationInternalService {

    /**
     * 批量处理待解约申请：扫一批 PENDING + SCANNING 记录，PENDING 逐条先查未结清扣费订单，
     * 再据此决定是发起支付平台解约还是置失败并通知 APP；SCANNING 主动查支付平台协议状态收口。
     * 调用方可反复调用直到 scanned 为 0。
     *
     * @param request 可为 null。null 或两个字段都为空时**不按申请时间过滤**，行为与历史一致；
     *                否则只处理 {@code REQUEST_TIME <= referenceTime - delayDays} 的记录，
     *                用于表达「解约申请满 N 天才确认」的业务口径
     */
    ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request);

    /**
     * 解约通知补偿：扫一批 APP_TERMINATION_REQUEST 中 NOTIFY_STATUS=FAILED 且未超重试上限的记录重发通知。
     * 无入参，由外部定时任务调度。
     *
     * <p>{@code AppNotifyService.compensateNotify()} 只扫 APP_PAY_SIGN_REQUEST，覆盖不到解约表，
     * 两者 **NEVER** 互相替代。</p>
     */
    CompensateNotifyRespDTO compensateTerminationNotify();

    /**
     * 通道清理补偿：重推「解约已收口成 SUCCESS、但账户域支付通道还没删掉」的记录（ADR-D48）。
     *
     * <p>捞 {@code TERMINATION_STATUS='SUCCESS'} 且 {@code CHANNEL_SYNC_STATUS} 为 {@code FAILED}
     * 或滞留 {@code PENDING} 的行。{@code NULL}（改造前的历史行）与 {@code MANUAL}（人工介入态）
     * 都**刻意扫不到**，理由见 {@code docs/domain/outbox.md} 的四个坑。
     *
     * <p>与 {@link #compensateTerminationNotify()} 管的是**两件不同的事**：那个补 APP 通知，
     * 这个补跨域的通道清理，**NEVER 互相替代、NEVER 合并成一个端点**。
     *
     * <p>调用方是 web-admin 的 Quartz 任务，**本模块 NEVER 加 `@Scheduled`**。
     */
    CompensateNotifyRespDTO compensateChannelSync();


    /**
     * 查询用户是否存在扣费失败订单。
     */
    CheckFailedOrdersRespDTO checkFailedOrders(CheckFailedOrdersReqDTO request);

    /**
     * 执行支付平台解约。
     *
     * <p>解约申请不存在时按签约记录补建一条 PENDING 申请再执行；签约记录也不存在则返回「用户未签约」。</p>
     */
    BaseRespDTO executeTermination(ExecuteTerminationReqDTO request);

    /**
     * IF8A-75 直接解绑支付方式：APP 侧入口，立即向支付渠道发起解绑，不等账期结束的定时任务。
     *
     * <p>与 IF8A-36 {@code requestTermination} 的区别是后者只登记 PENDING、真正发起要等
     * web-admin 的 {@code TerminationQuartzTask} 扫表；本接口内部直接复用
     * {@link #executeTermination}（登记缺失时按签约记录补建 → CAS 置 SCANNING → 立即调支付渠道）。</p>
     *
     * <p><b>不校验未结清欠费</b>（用户 2026-09-08 裁决）：规格 §3.59 的场景是「用户长时间未登录需强制
     * 解除绑定关系」，欠费由后续催收流程处理。IF8A-42 销户入口已有 {@code 8023} 拦截，此处不重复拦。</p>
     *
     * @return {@code 0000} 表示<b>已向支付渠道发起</b>，不代表已解绑完成，收口以
     *         {@code processTermination} 主动查 §2.4 {@code queryResult} 为准
     */
    UnbindAgreementResult unbindAgreement(UnbindAgreementReqDTO request);

    /**
     * 通知 APP 解约失败。
     */
    BaseRespDTO notifyTerminationFailed(NotifyTerminationFailedReqDTO request);
}
