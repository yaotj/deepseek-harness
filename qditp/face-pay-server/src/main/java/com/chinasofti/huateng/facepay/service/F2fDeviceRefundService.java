package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestRefundReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备侧主动退款（TVM {@code requestRefund}）。把设备报文翻译成 {@link RefundCommand}，
 * 校验后交给 {@link F2fRefundService}，再把结果翻回设备契约。
 *
 * <h2>相对旧实现补上的三道校验</h2>
 * <ol>
 *   <li><b>订单必须已支付</b>（白名单 {@code PAID / FULFILLED / FULFILL_FAILED}）。
 *       旧实现只在 TVM 购票分支里校验了 {@code STATUS='1'}，充值分支同样校验，
 *       但 <b>BOM（transType=04）分支根本不存在</b>，直接回「交易类型不明确」。</li>
 *   <li><b>退款金额不得超过订单金额</b>。旧实现 {@code Integer.valueOf(refundAmt)} 裸转换、
 *       不校验上限，设备传多少就退多少。</li>
 *   <li><b>金额非数字时按 9999 拒绝</b>，而不是抛异常退化成 UUID retCode。</li>
 * </ol>
 *
 * <p><b>2026-09-15 起本类不再推进 {@code ORDER_STATUS}</b>（ADR-D88）：退款与支付/履约主状态正交，
 * 收口只写 {@code F2F_REFUND} + 订单上的退款汇总三列。原先靠「CAS 到 {@code REFUNDING} 返 0 行」
 * 顺带识别重复退款，那条信号消失后由两处接替：<b>跨来源在途退款</b>用
 * {@code countUnsettledRefunds} 显式拦（同来源仍由 {@code UK_F2F_REFUND_IDEM} 兜底），
 * <b>超额退款</b>用「订单金额 - 已退金额」判据拦。<b>NEVER 把 CAS 加回来</b> ——
 * 主状态不再有 {@code REFUNDING} 这条边，加回去只会恒返 0 行、刷满假告警。</p>
 *
 * <p>响应契约照搬旧实现：成功只回 {@code retCode=0000 / retMsg=成功}（<b>不回退款单号</b>），
 * 失败回 {@code retCode=9999}，<b>但「订单不存在」是 {@code 2002}</b>（旧实现在这一支走参数校验族，
 * 见 {@link TvmResponses#refundOrderNotFound()}）。<b>「已受理」不等于「已退成功」</b>——
 * 钱到账要等 {@code F2fRefundService.reconcileRefund} 收口，但设备契约就是这样，NEVER 改。</p>
 */
@Service
public class F2fDeviceRefundService {

    /** 可退款的订单状态白名单：钱已收到、且业务已结束或已失败。 */
    private static final List<String> REFUNDABLE = F2fOrderStatus.REFUNDABLE;

    private static final Logger log = LoggerFactory.getLogger(F2fDeviceRefundService.class);

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fRefundService refundService;

    public F2fDeviceRefundService(F2fOrderMapper orderMapper, F2fPaymentMapper paymentMapper,
                                  F2fRefundService refundService) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.refundService = refundService;
    }

    /** TVM 主动退款。<b>整单退</b>，{@code ticketLogicNum} 传 null（幂等键用 {@code #WHOLE#} 占位）。 */
    public JSONObject requestRefund(RequestRefundReqDTO request) {
        Long amount = request.amountInFen();
        if (amount == null) {
            log.warn("TVM 退款金额非法, orderNo={}, refundAmt={}", request.getOrderNo(), request.getRefundAmt());
            return TvmResponses.refundFail("退款金额格式非法");
        }
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.warn("TVM 退款 订单不存在, orderNo={}", request.getOrderNo());
            return TvmResponses.refundOrderNotFound();
        }
        if (!REFUNDABLE.contains(order.getOrderStatus())) {
            log.warn("TVM 退款 订单状态不可退, orderNo={}, status={}",
                    request.getOrderNo(), order.getOrderStatus());
            return TvmResponses.refundFail("订单状态不是支付成功,不能退款");
        }
        int unsettled = orderMapper.countUnsettledRefunds(request.getOrderNo());
        if (unsettled > 0) {
            log.warn("TVM 退款 该订单尚有未收口的退款单，拒绝再退, orderNo={}, unsettled={}",
                    request.getOrderNo(), unsettled);
            return TvmResponses.refundFail("该订单有退款正在处理中,请稍后再试");
        }
        if (order.getOrderAmount() != null) {
            long refunded = order.getRefundAmount() == null ? 0L : order.getRefundAmount();
            if (amount > order.getOrderAmount() - refunded) {
                log.error("TVM 退款金额超过可退金额被拒, orderNo={}, refundAmt={}, orderAmount={}, refunded={}",
                        request.getOrderNo(), amount, order.getOrderAmount(), refunded);
                return TvmResponses.refundFail("退款金额不能大于订单金额");
            }
        }

        F2fPayment payment = paymentMapper.selectLastAttempt(request.getOrderNo());
        RefundCommand command = new RefundCommand(request.getOrderNo(), null,
                F2fRefundService.SOURCE_TVM_REQUEST, amount, null,
                request.getRefundReason(), request.getDeviceId(), order.getOperatorId(),
                order.getTransType(), null, payment == null ? null : payment.getPayCenterOrderNo());
        RefundOutcome outcome = refundService.refund(command);

        if (outcome.isRejected()) {
            return TvmResponses.refundFail(outcome.failureReason());
        }
        if (outcome.alreadyExisted()) {
            log.info("TVM 退款幂等命中已有退款单, orderNo={}, refundNo={}, status={}",
                    request.getOrderNo(), outcome.refundNo(), outcome.refundStatus());
            return refundAccepted(outcome);
        }
        if (!outcome.isAccepted()) {
            log.error("TVM 退款未被支付中心受理，已落库待扫表重试, orderNo={}, refundNo={}",
                    request.getOrderNo(), outcome.refundNo());
            return TvmResponses.refundFail("退款请求已受理，结果稍后确认");
        }
        log.info("TVM 退款已受理, orderNo={}, refundNo={}", request.getOrderNo(), outcome.refundNo());
        return refundAccepted(outcome);
    }

    /**
     * 把退款单当前状态翻译成设备侧的 {@code refundResult}。
     *
     * <p><b>只有终态 {@code SUCCESS} 才回 {@code SUCCESS}</b>：本接口是「发起退款」，
     * 支付中心受理（{@code PROCESSING}）不等于钱已到账，收口要等
     * {@code F2fRefundService.reconcileRefund} 回查。其余非终态（{@code INIT} /
     * {@code MANUAL}）一律按 {@code PROCESSING} 上报——对设备来说都是「还没有结论」，
     * <b>NEVER 把它们映射成 {@code FAILED}</b>，那会让设备把一笔仍在处理中的退款当成失败。</p>
     */
    private static JSONObject refundAccepted(RefundOutcome outcome) {
        boolean success = F2fRefundService.STATUS_SUCCESS.equals(outcome.refundStatus());
        return TvmResponses.refundSuccess(success ? "SUCCESS" : "PROCESSING",
                success ? "退款成功" : "退款处理中", outcome.refundNo());
    }
}
