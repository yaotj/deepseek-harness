package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.port.DebitSyncPort;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayCallbackService;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.RefundCallbackSettler;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.TxnRefundCallbackSettler;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 支付中心回调编排（{@link AlipayPayCallbackService} 的唯一实现）。
 *
 * <p>支付回调五步，顺序 MUST 不变：① 解析成 {@link PayNotifyCommand} → ② <b>落证据</b> →
 * ③ <b>计数</b> → ④ <b>回写本表支付状态</b> → ⑤ 出网通知 gate-txn-pay 收敛扣费状态 →
 * ⑥ 按结果回写处置状态并组装响应。
 *
 * <p><b>④ MUST 在 ⑤ 之前</b>：回调是「对方主动告知的支付事实」，支付侧的事实要先落到
 * {@code ALIPAY_PAY_TXN_DETAIL}。反过来写时，一旦 ⑤ 失败且已达推送上限，本方法会
 * {@code markPayManual} 并返 {@code 0000} 让对端停推 —— 那条路径下本表的 {@code PAY_STATUS}
 * 就永远停在 {@code requestPay} 那一刻的值，支付中心明说成功而我方账面是失败，且再没有人来纠正。
 * 先写本地不会有对称的风险：{@code updatePayCallback} 带状态白名单（{@code SUCCESS} 不在其中），
 * 对端重推时第二次影响 0 行即幂等命中。
 *
 * <p><b>② 与 ③ 的先后 NEVER 反序</b>（与 pay-sign-server 同口径）：本方法无事务、INSERT 已自动提交，
 * 因此 COUNT 才等于支付中心的实际推送次数（<b>含本次</b>）。反过来写每次都少一，
 * 限次永远差一轮才触发 —— 而这个偏差在单次联调里完全看不出来。
 *
 * <p><b>NEVER 加 {@code @Transactional}</b>：链路内有出网，事务会把行锁持有时长拉成对端响应时长；
 * 且回滚会把「留证据」那条 INSERT 一起丢掉，限次判定随之失效 —— pay-sign 侧 2026-08-26 的生产事故
 * （循环重推 8 分钟、期间回调表零条落库）就是这个形态。
 *
 * <p><b>与旧实现互不调用</b>：{@code PaymentQueryService.handlePayNotify} 与
 * {@code PaymentRefundService.handleRefundNotify} 原样保留作回滚位，本类 NEVER 转发给它们、
 * 它们也 NEVER 反向调本类。回滚只需把 {@code PayCenterCallbackController} 改回注
 * {@code AlipayTripPaymentService}。
 */
@Service
public class AlipayPayCallbackServiceImpl implements AlipayPayCallbackService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayCallbackServiceImpl.class);

    /** 支付结果回调允许支付中心推送的总次数（首推 1 次 + 重推 1 次），与 pay-sign-server 同口径。 */
    private static final int MAX_PAY_CALLBACK_PUSH = 2;

    private final CallbackLogRepository callbackLogRepository;
    private final PayTxnCallbackWriter payTxnCallbackWriter;
    private final DebitSyncPort debitSyncPort;
    private final RefundCallbackSettler refundCallbackSettler;
    private final TxnRefundCallbackSettler txnRefundCallbackSettler;

    public AlipayPayCallbackServiceImpl(CallbackLogRepository callbackLogRepository,
                                       PayTxnCallbackWriter payTxnCallbackWriter,
                                       DebitSyncPort debitSyncPort,
                                       RefundCallbackSettler refundCallbackSettler,
                                       TxnRefundCallbackSettler txnRefundCallbackSettler) {
        this.callbackLogRepository = callbackLogRepository;
        this.payTxnCallbackWriter = payTxnCallbackWriter;
        this.debitSyncPort = debitSyncPort;
        this.refundCallbackSettler = refundCallbackSettler;
        this.txnRefundCallbackSettler = txnRefundCallbackSettler;
    }

    @Override
    public AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request) {
        log.info("接收到支付宝支付结果回调, 原始报文: {}", JSON.toJSONString(request));
        return switch (PayNotifyCommand.from(request)) {
            case PayNotifyCommand.MissingOrderNo ignored -> {
                log.warn("支付宝支付回调报文为空或订单号为空");
                yield response(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "参数异常：orderNo不能为空");
            }
            case PayNotifyCommand.IllegalTransStatus illegal -> {
                log.error("支付宝支付回调 transStatus 不在白名单内，拒绝处理、MUST 人工核对报文契约, orderNo={}, transStatus={}",
                        illegal.orderNo(), illegal.rawTransStatus());
                callbackLogRepository.recordPayCallback(request, CallbackLogRepository.HANDLE_STATUS_MANUAL,
                        "transStatus 非法: " + illegal.rawTransStatus());
                yield response(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "参数异常：transStatus 取值非法");
            }
            case PayNotifyCommand.Accepted accepted -> onAccepted(request, accepted);
        };
    }

    /**
     * 报文合法后的处置。
     *
     * <p>失败分支的两支处置刻意不同：
     * <ul>
     *   <li>**未达上限** → 处置置 {@code FAIL} + 返 {@code 9999}，让支付中心再推一轮；</li>
     *   <li>**已达上限** → 把最后一条置 {@code MANUAL} 并**返 {@code 0000} 让它停推**。
     *       这条是刻意的：继续让对端无休止重推既不会成功、又会持续制造证据行。
     *       <b>代价是「真成功」与「放弃重推」对外都是 {@code 0000}、外部无法区分</b>，
     *       因此运维 MUST 例行巡检 {@code HANDLE_STATUS='MANUAL'}，否则放弃的那笔会静默沉底。</li>
     * </ul>
     */
    private AlipayCommonResponse onAccepted(AlipayTripPayNotifyReqDTO request, PayNotifyCommand.Accepted accepted) {
        String callbackSeq = callbackLogRepository.recordPayCallback(request,
                CallbackLogRepository.HANDLE_STATUS_PROCESSING, null);
        int pushCount = callbackLogRepository.countPayPush(accepted.orderNo());

        log.info("支付宝支付回调,准备同步扣费订单状态, orderNo={}, transStatus={}, payStatus={}, channelVoucherId={}, transTime={}, transAmount={}, pushCount={}",
                accepted.orderNo(), accepted.rawTransStatus(), accepted.payStatus(),
                accepted.channelVoucherId(), accepted.transTime(), accepted.transAmount(), pushCount);

        payTxnCallbackWriter.applyCallback(accepted.orderNo(), accepted.payStatus(), accepted.channelVoucherId(),
                accepted.transTime());

        if (!syncGateTxnPayStatus(accepted.orderNo(), accepted.payStatus())) {
            if (pushCount >= MAX_PAY_CALLBACK_PUSH) {
                log.error("支付宝支付回调已推送{}次仍未处理成功，达到上限{}，放弃重推并返回0000，MUST 人工处理, orderNo={}, 原因={}",
                        pushCount, MAX_PAY_CALLBACK_PUSH, accepted.orderNo(), "扣费订单状态同步失败");
                callbackLogRepository.markPayManual(accepted.orderNo(), "扣费订单状态同步失败");
                return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "成功");
            }
            callbackLogRepository.updateHandleResult(callbackSeq, CallbackLogRepository.HANDLE_STATUS_FAIL,
                    "扣费订单状态同步失败，待支付中心重推");
            return response(FepAppErrorCodeEnum.FAIL.getCode(), "扣费订单状态同步失败");
        }

        callbackLogRepository.updateHandleResult(callbackSeq, CallbackLogRepository.HANDLE_STATUS_SUCCESS, null);
        log.info("支付宝支付回调处理完成, orderNo={}, payStatus={}", accepted.orderNo(), accepted.payStatus());
        return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "成功");
    }

    @Override
    public AlipayCommonResponse handleRefundNotify(AlipayTripRefundNotifyReqDTO request) {
        log.info("接收到支付宝退款结果回调, 原始报文: {}", JSON.toJSONString(request));
        return switch (RefundNotifyCommand.from(request)) {
            case RefundNotifyCommand.MissingOrderNo ignored -> {
                log.warn("支付宝退款回调报文为空或订单号为空");
                yield response(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "参数异常：orderNo不能为空");
            }
            case RefundNotifyCommand.Accepted accepted -> onRefundAccepted(request, accepted);
        };
    }

    /**
     * 退款回调三步：① 落 {@code PROCESSING} 证据 → ② 按 {@code refundResult} 收口退款明细与汇总 →
     * ③ 按收口归宿回写处置状态。
     *
     * <p><b>一律返 {@code 0000}</b>：退款明细的收口是 CAS，重推第二次影响 0 行即幂等命中，
     * 让支付中心重推不会带来任何新信息；真正没收口的形态（{@code MANUAL}）靠运维巡检与
     * pay-sign 侧那套退款回查补偿捞回来。<b>NEVER 改成失败时返 {@code 9999}</b> ——
     * 本表没有退款方向的推送次数上限判定，返 9999 等于让对端无休止重推、持续制造证据行。
     *
     * <p><b>NEVER 把 ② 挪到 ① 之前</b>：留证据是唯一能事后举证「支付中心确实推过、推的是什么」的载体，
     * 先收口再落证据时，收口过程中抛异常那一轮就什么痕迹都没有。
     */
    private AlipayCommonResponse onRefundAccepted(AlipayTripRefundNotifyReqDTO request,
                                                  RefundNotifyCommand.Accepted accepted) {
        String callbackSeq = callbackLogRepository.recordRefundCallback(request, accepted,
                CallbackLogRepository.HANDLE_STATUS_PROCESSING, null);
        log.info("支付宝退款回调已留证据，准备收口退款明细, orderNo={}, refundNo={}, outRefundNo={}, refundResult={}, refundAmount={}",
                accepted.orderNo(), request.getRefundNo(), accepted.refundOrderNo(), accepted.refundResult(),
                accepted.refundAmount());

        RefundCallbackSettler.Outcome outcome = refundCallbackSettler.settle(accepted.orderNo(),
                accepted.refundOrderNo(), accepted.refundResult(), request.getRefundResultDesc());
        if (outcome == RefundCallbackSettler.Outcome.NOT_MATCHED) {
            outcome = txnRefundCallbackSettler.settle(accepted.orderNo(), accepted.refundOrderNo(),
                    accepted.refundResult(), request.getRefundResultDesc());
        }

        switch (outcome) {
            case SETTLED_SUCCESS -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_SUCCESS, "退款明细已收口为 SUCCESS，汇总已重算");
            case SETTLED_FAIL -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_SUCCESS, "退款明细已收口为 FAIL");
            case STILL_PROCESSING -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_PROCESSING, "退款仍处理中，等待终态回调");
            case NOT_MATCHED -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_SUCCESS, "未命中处理中明细，按幂等处理（重推或已收口）");
            case UNKNOWN_RESULT -> callbackLogRepository.updateHandleResult(callbackSeq,
                    CallbackLogRepository.HANDLE_STATUS_MANUAL,
                    "refundResult 取值不在契约内: " + accepted.refundResult());
        }
        return response(FepAppErrorCodeEnum.SUCCESS.getCode(), "成功");
    }


    /**
     * 通知 gate-txn-pay-server 把 {@code GATE_TXN_PAY.DEBIT_STATUS} 收敛到终态。
     *
     * <p>三态判读收口在 {@link DebitSyncPort} 的 adapter 里（ADR-D131），本方法只把它折回
     * 「要不要让支付中心重推」这一个 boolean。{@code BizRejected} 理论上可以直接标 MANUAL ——
     * 业务拒绝重推一万次也不会成功；但那是**行为变更**且涉及支付回调，
     * MUST 先与 gate-txn-pay 的 retCode 语义对齐后再改，本次迁移对上层语义逐字不变。
     *
     * @return true 表示远端已确认收敛（含幂等命中），false 表示需要支付中心重推
     */
    private boolean syncGateTxnPayStatus(String orderNo, String payStatus) {
        RpcOutcome outcome = debitSyncPort.syncDebitStatus(orderNo, payStatus, "支付宝出行扣费结果回调");
        switch (outcome) {
            case RpcOutcome.Ok ok -> {
                log.info("扣费订单状态同步成功, orderNo={}, payStatus={}", orderNo, payStatus);
                return true;
            }
            case RpcOutcome.BizRejected rejected -> {
                log.error("扣费订单状态同步被业务拒绝，待支付中心重推, orderNo={}, payStatus={}, retCode={}, retMsg={}",
                        orderNo, payStatus, rejected.retCode(), rejected.retMsg());
                return false;
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("扣费订单状态同步未获答复，待支付中心重推, orderNo={}, payStatus={}, cause={}",
                        orderNo, payStatus, unreachable.cause().getClass().getSimpleName());
                return false;
            }
        }
    }

    private AlipayCommonResponse response(String retCode, String retMsg) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}
