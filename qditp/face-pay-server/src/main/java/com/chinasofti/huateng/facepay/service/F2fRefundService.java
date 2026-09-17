package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.entity.F2fRefund;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fRefundMapper;
import com.chinasofti.huateng.facepay.support.F2fOrderNo;
import com.chinasofti.huateng.facepay.support.F2fOrderNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 退款编排。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fRefundService {

    /** 出票故障自动退（差额退）。 */
    public static final String SOURCE_TAKE_TICKET_FAIL = "TAKE_TICKET_FAIL";

    /** BOM 单程票原路退。 */
    public static final String SOURCE_BOM_ORIGINAL = "BOM_ORIGINAL";

    /** APP 用户主动退。 */
    public static final String SOURCE_APP_REQUEST = "APP_REQUEST";

    /** 每日批量退未取票交易。 */
    public static final String SOURCE_DAILY_BATCH = "DAILY_BATCH";

    /** 充值写卡失败退。 */
    public static final String SOURCE_TOPUP_FAIL = "TOPUP_FAIL";

    /** 运营端手工退。 */
    public static final String SOURCE_PAGE_MANUAL = "PAGE_MANUAL";

    /** 设备侧 {@code requestRefund} 发起。 */
    public static final String SOURCE_TVM_REQUEST = "TVM_REQUEST";

    static final String STATUS_INIT = "INIT";

    static final String STATUS_PROCESSING = "PROCESSING";

    static final String STATUS_SUCCESS = "SUCCESS";

    static final String STATUS_FAILED = "FAILED";

    /** 超过自动收口时间窗仍拿不到明确结果，转人工介入。 */
    static final String STATUS_MANUAL = "MANUAL";

    /** 可推进到终态的前置状态白名单。 */
    private static final List<String> PENDING_STATUSES = List.of(STATUS_INIT, STATUS_PROCESSING);

    private static final Logger log = LoggerFactory.getLogger(F2fRefundService.class);

    private final F2fRefundMapper refundMapper;

    /** 只用于退款收口后重算订单上的退款汇总三列（{@code REFUND_STATUS} / {@code REFUND_AMOUNT} / {@code LAST_REFUND_TMS}）。 */
    private final F2fOrderMapper orderMapper;

    private final F2fOrderNoGenerator orderNoGenerator;

    private final PayCenterClient payCenterClient;

    private final PayCenterMessageFactory messageFactory;

    /** 退避与放弃的全部参数与算法。 */
    private final F2fRefundRetryPolicy retryPolicy;

    public F2fRefundService(F2fRefundMapper refundMapper, F2fOrderMapper orderMapper,
                            F2fOrderNoGenerator orderNoGenerator,
                            PayCenterClient payCenterClient, PayCenterMessageFactory messageFactory,
                            F2fRefundRetryPolicy retryPolicy) {
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.payCenterClient = payCenterClient;
        this.messageFactory = messageFactory;
        this.retryPolicy = retryPolicy;
    }

    /**
     * 发起一笔退款。
     *
     * @return 退款结果；参数非法时返回 {@link RefundOutcome#rejected(String)}
     */
    public RefundOutcome refund(RefundCommand command) {
        String reject = command.validate();
        if (reject != null) {
            log.warn("退款请求参数非法被拒, command={}, reason={}", command, reject);
            return RefundOutcome.rejected(reject);
        }

        String refundNo = orderNoGenerator.next(F2fOrderNo.BIZ_REFUND);
        F2fRefund refund = command.toEntity(refundNo, LocalDateTime.now());
        refund.setNextQueryTms(retryPolicy.firstQueryTms(refund.getRequestTms()));
        try {
            refundMapper.insert(refund);
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            F2fRefund existing = findExisting(command);
            if (existing == null) {
                log.error("退款单撞唯一索引却查不回已有单，需人工核查, origOrderNo={}, source={}",
                        command.origOrderNo(), command.refundSource());
                return RefundOutcome.rejected("退款单状态异常，请人工核查");
            }
            log.info("该笔已退过，幂等返回已有退款单, refundNo={}, status={}",
                    existing.getRefundNo(), existing.getRefundStatus());
            return new RefundOutcome(existing.getRefundNo(), existing.getRefundStatus(), true, null);
        }
        log.info("退款单已落库, refundNo={}, origOrderNo={}, source={}, amount={}",
                refundNo, command.origOrderNo(), command.refundSource(), command.refundAmount());

        return submitToPayCenter(refundNo, command);
    }

    /** 把退款单送到支付中心。 */
    private RefundOutcome submitToPayCenter(String refundNo, RefundCommand command) {
        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getRefundUrl(),
                messageFactory.buildRefundRequest(refundNo, command.origOrderNo(),
                        command.payCenterOrderNo(), command.refundAmount()));

        if (result.isTransportFailed() || !result.isSuccessCode()) {
            String reason = result.isTransportFailed() ? result.getFailureReason() : result.getMsg();
            refundMapper.increaseRetryTimes(refundNo, truncate(reason), retryPolicy.nextQueryTms(0));
            log.error("退款未被支付中心受理，留在 INIT 等扫表收口（NEVER 置 FAILED，钱可能已退）,"
                    + " refundNo={}, transportFailed={}, code={}", refundNo, result.isTransportFailed(),
                    result.getCode());
            return new RefundOutcome(refundNo, STATUS_INIT, false, reason);
        }

        int updated = refundMapper.updateStatus(refundNo, List.of(STATUS_INIT), STATUS_PROCESSING,
                result.string("refundOrderNo"), null);
        log.info("退款已被支付中心受理, refundNo={}, updatedRows={}", refundNo, updated);
        return new RefundOutcome(refundNo, STATUS_PROCESSING, false, null);
    }

    /**
     * 退款收口：向支付中心查这笔退款的最终结果并推进本地状态。
     *
     * @return true 表示本轮已收口到终态（无需再扫），false 表示状态仍不明、已排下一轮
     */
    public boolean reconcileRefund(F2fRefund refund) {
        String refundNo = refund.getRefundNo();
        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getRefundQueryUrl(),
                messageFactory.buildRefundQueryRequest(refundNo));

        if (result.isTransportFailed() || !result.isSuccessCode()) {
            String reason = result.isTransportFailed() ? result.getFailureReason() : result.getMsg();
            if (giveUpToManual(refund, reason)) {
                return true;
            }
            refundMapper.increaseRetryTimes(refundNo, truncate(reason),
                    retryPolicy.nextQueryTms(refund.getRetryTimes()));
            log.warn("退款收口未拿到有效结果，本轮不动状态, refundNo={}, transportFailed={}",
                    refundNo, result.isTransportFailed());
            return false;
        }

        PayCenterStatus status = result.status();
        LocalDateTime now = LocalDateTime.now();
        if (status == PayCenterStatus.SUCCESS) {
            int updated = refundMapper.updateStatus(refundNo, PENDING_STATUSES, STATUS_SUCCESS,
                    result.string("refundOrderNo"), now);
            log.info("退款收口为成功, refundNo={}, updatedRows={}", refundNo, updated);
            refreshOrderRefundSummary(refund);
            return true;
        }
        if (status != null && status.isFailed()) {
            int updated = refundMapper.updateStatus(refundNo, PENDING_STATUSES, STATUS_FAILED,
                    result.string("refundOrderNo"), now);
            log.error("退款收口为失败，需人工介入, refundNo={}, updatedRows={}", refundNo, updated);
            refreshOrderRefundSummary(refund);
            return true;
        }
        String pending = "支付中心仍报 " + status;
        if (giveUpToManual(refund, pending)) {
            return true;
        }
        refundMapper.increaseRetryTimes(refundNo, truncate(pending),
                retryPolicy.nextQueryTms(refund.getRetryTimes()));
        log.info("退款收口时{}，按退避排下一轮, refundNo={}", pending, refundNo);
        return false;
    }

    /** 退款单收口到终态后，重算订单上的退款汇总三列。 */
    private void refreshOrderRefundSummary(F2fRefund refund) {
        String origOrderNo = refund.getOrigOrderNo();
        if (origOrderNo == null || origOrderNo.isBlank()) {
            log.error("退款单没有原订单号，无法重算订单退款汇总, refundNo={}", refund.getRefundNo());
            return;
        }
        int updated = orderMapper.updateRefundSummary(origOrderNo);
        log.info("订单退款汇总已重算, orderNo={}, refundNo={}, updatedRows={}",
                origOrderNo, refund.getRefundNo(), updated);
    }

    /**
     * 次数用尽或悬挂超过时间窗就置 {@link #STATUS_MANUAL}，停止自动收口并留 ERROR 告警。
     *
     * @return true 表示已转人工终态，调用方 MUST 直接返回、NEVER 再累加重试
     */
    private boolean giveUpToManual(F2fRefund refund, String reason) {
        LocalDateTime requestTms = refund.getRequestTms();
        int queried = retryPolicy.queriedTimes(refund.getRetryTimes());
        boolean timesExhausted = retryPolicy.timesExhausted(refund.getRetryTimes());
        boolean windowExpired = retryPolicy.windowExpired(requestTms);
        if (!timesExhausted && !windowExpired) {
            return false;
        }
        String refundNo = refund.getRefundNo();
        int updated = refundMapper.updateStatus(refundNo, PENDING_STATUSES, STATUS_MANUAL,
                null, LocalDateTime.now());
        log.error("退款收口放弃自动判定，转人工介入（钱是否已退未知，MUST 查支付中心或走对账）,"
                        + " refundNo={}, 次数用尽={}, 超时间窗={}, requestTms={}, queried={}/{},"
                        + " 时间窗={}小时, lastReason={}, updatedRows={}",
                refundNo, timesExhausted, windowExpired, requestTms, queried,
                retryPolicy.getMaxQueryTimes(), retryPolicy.getGiveUpAfterHours(), reason, updated);
        return true;
    }

    /** 扫一批到点该查的退款单，供定时任务调用。 */
    public List<F2fRefund> loadRetryCandidates(LocalDateTime now, int limit) {
        return refundMapper.selectRetryCandidates(PENDING_STATUSES, now, limit);
    }

    /** 按退款单号查询，供运营端与通知链路使用。 */
    public F2fRefund findByRefundNo(String refundNo) {
        return refundMapper.selectByRefundNo(refundNo);
    }

    /** 撞唯一索引后回查已有退款单。 */
    private F2fRefund findExisting(RefundCommand command) {
        List<F2fRefund> refunds = refundMapper.selectByOrigOrderNo(command.origOrderNo());
        for (F2fRefund candidate : refunds) {
            if (Objects.equals(candidate.getTicketLogicNum(), command.ticketLogicNum())
                    && command.refundSource().equals(candidate.getRefundSource())) {
                return candidate;
            }
        }
        return null;
    }

    /** {@code FAIL_REASON} 列宽 512，超长截断由应用负责（mapper 文档已注明）。 */
    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 512 ? reason : reason.substring(0, 512);
    }
}
