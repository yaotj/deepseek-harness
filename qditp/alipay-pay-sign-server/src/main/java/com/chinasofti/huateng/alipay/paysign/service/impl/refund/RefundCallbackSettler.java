package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 退款回调对 {@code ALIPAY_REFUND_LOG} / {@code ALIPAY_PAY_LOG} 的收口门面。
 *
 * <p><b>为什么要这个类</b>：明细与汇总的唯一写入者 {@link RefundLogRepository} 是包私有的
 * （状态字面量集中在它那里，见其类注释），而回调编排在 {@code service.impl.callback} 包，
 * 跨包注不进去。<b>NEVER 把 {@code RefundLogRepository} 改成 public</b> —— 那等于把明细写入
 * 敞开给全模块；<b>也 NEVER 在 callback 包里再抄一份状态字面量与 mapper 调用</b>，
 * 那会立刻出现「两处对同一个状态值的理解不同」。
 *
 * <p><b>NEVER 加 {@code @Transactional}</b>：调用方所在的回调链路要先落证据、再出网，
 * 事务包住会把「留证据」的 INSERT 一起回滚（AGENTS.md §5.2 已有生产事故）。
 * 本类两步（明细 CAS + 汇总重算）各自自动提交，中途失败的形态由退款回查补偿收口。
 */
@Component
public class RefundCallbackSettler {

    private static final Logger log = LoggerFactory.getLogger(RefundCallbackSettler.class);

    /** 支付中心 §5.2 退款回调的 {@code refundResult} 取值：成功。 */
    private static final String CALLBACK_RESULT_SUCCESS = "SUCCESS";
    /** 支付中心 §5.2 退款回调的 {@code refundResult} 取值：失败。 */
    private static final String CALLBACK_RESULT_FAIL = "FAIL";

    private final RefundLogRepository refundLogRepository;

    RefundCallbackSettler(RefundLogRepository refundLogRepository) {
        this.refundLogRepository = refundLogRepository;
    }

    /** 回调收口的四种归宿，调用方据此回写回调日志的处置状态。 */
    public enum Outcome {
        /** 明细已由 {@code PROCESSING} 收口为 {@code SUCCESS}，汇总已重算。 */
        SETTLED_SUCCESS,
        /** 明细已由 {@code PROCESSING} 收口为 {@code FAIL}。 */
        SETTLED_FAIL,
        /** {@code refundResult=PROCESSING}，本次刻意不回写，等终态回调或回查补偿。 */
        STILL_PROCESSING,
        /** CAS 影响 0 行：重推、已被回查补偿收口，或该退款单号不属于本方。 */
        NOT_MATCHED,
        /** {@code refundResult} 不在 §5.2 的三个取值内，NEVER 猜，转人工。 */
        UNKNOWN_RESULT
    }

    /**
     * 按商户退款单号收口一次退款回调。
     *
     * @param orderNo       原商户订单号，只用于汇总重算与日志
     * @param refundOrderNo 商户退款单号（回调的 {@code outRefundNo}，<b>NEVER 传 {@code refundNo}</b>，
     *                      后者是支付中心侧流水、本表没有它的索引）
     * @param refundResult  支付中心 §5.2 的 {@code refundResult}
     * @param resultMsg     回写进 {@code RESULT_MSG} 的描述，取回调的 {@code refundResultDesc}
     */
    public Outcome settle(String orderNo, String refundOrderNo, String refundResult, String resultMsg) {
        if (CALLBACK_RESULT_SUCCESS.equalsIgnoreCase(refundResult)) {
            return settleTerminal(orderNo, refundOrderNo, RefundLogRepository.REFUND_STATUS_SUCCESS, resultMsg,
                    Outcome.SETTLED_SUCCESS);
        }
        if (CALLBACK_RESULT_FAIL.equalsIgnoreCase(refundResult)) {
            return settleTerminal(orderNo, refundOrderNo, RefundLogRepository.REFUND_STATUS_FAIL, resultMsg,
                    Outcome.SETTLED_FAIL);
        }
        if (RefundLogRepository.REFUND_STATUS_PROCESSING.equalsIgnoreCase(refundResult)) {
            log.info("退款回调仍为处理中，本次不回写明细, orderNo={}, refundOrderNo={}", orderNo, refundOrderNo);
            return Outcome.STILL_PROCESSING;
        }
        log.error("退款回调 refundResult 不在契约取值内，MUST 人工核对, orderNo={}, refundOrderNo={}, refundResult={}",
                orderNo, refundOrderNo, refundResult);
        return Outcome.UNKNOWN_RESULT;
    }

    private Outcome settleTerminal(String orderNo, String refundOrderNo, String refundStatus, String resultMsg,
                                   Outcome settled) {
        int affected = refundLogRepository.settleFromCallback(refundOrderNo, refundStatus, resultMsg);
        if (affected == 0) {
            log.warn("退款回调收口未命中处理中明细，按幂等处理, orderNo={}, refundOrderNo={}, refundStatus={}",
                    orderNo, refundOrderNo, refundStatus);
            return Outcome.NOT_MATCHED;
        }
        if (RefundLogRepository.REFUND_STATUS_SUCCESS.equals(refundStatus)) {
            refundLogRepository.refreshSummary(orderNo, refundOrderNo);
        }
        log.info("退款回调已收口明细, orderNo={}, refundOrderNo={}, refundStatus={}", orderNo, refundOrderNo, refundStatus);
        return settled;
    }
}
