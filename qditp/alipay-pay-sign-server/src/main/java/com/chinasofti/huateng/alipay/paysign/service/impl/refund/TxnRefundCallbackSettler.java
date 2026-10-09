package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 退款回调对**新表** {@code ALIPAY_REFUND_TXN_DETAIL} / {@code ALIPAY_PAY_TXN_DETAIL} 的收口。
 *
 * <p><b>为什么必须有这个类</b>：{@link AlipayTxnRefundService} 申请退款后只能把明细写到
 * {@code PROCESSING}（退款申请的应答里没有结果字段，见那个类的 {@code applyReply} 注释），而既有的
 * {@link RefundCallbackSettler} 只认旧表 {@code ALIPAY_REFUND_LOG}。缺这一环，新表链路落下的
 * {@code PROCESSING} 行**永远没人收口**，而申请侧的幂等短路又「同一原订单存在 PROCESSING 即拒绝」——
 * 结果是**某个订单退一次之后再也退不了**。<b>NEVER 通过放宽那条幂等短路来绕开</b>：那等于允许对同一笔
 * 原支付重复出账。
 *
 * <p><b>与 {@link RefundCallbackSettler} 并存、职责按表正交</b>：一个 {@code refundOrderNo} 只会存在于
 * 两张表之一（旧链路生成的在旧表、新链路生成的在新表），因此调用方先试旧表、{@code NOT_MATCHED}
 * 再试本类。<b>NEVER 把两者合并成一个「同时写两张表」的实现</b> —— 那会让「这单走的是哪条链路」不可判定。
 *
 * <p><b>NEVER 加 {@code @Transactional}</b>：调用方所在的回调链路要先落证据再收口，事务包住会把
 * 「留证据」的 INSERT 一起回滚（AGENTS.md §5.2 已有生产事故）。本类两步（明细 CAS + 汇总重算）
 * 各自自动提交。
 */
@Component
public class TxnRefundCallbackSettler {

    private static final Logger log = LoggerFactory.getLogger(TxnRefundCallbackSettler.class);

    /** 支付中心 §5.2 退款回调的 {@code refundResult} 取值。 */
    private static final String CALLBACK_RESULT_SUCCESS = "SUCCESS";
    private static final String CALLBACK_RESULT_FAIL = "FAIL";
    private static final String CALLBACK_RESULT_PROCESSING = "PROCESSING";

    /** 本表状态字面量；与 {@code AlipayTxnRefundService} 那组同源，改一处 MUST 看齐另一处。 */
    private static final String REFUND_STATUS_SUCCESS = "SUCCESS";
    private static final String REFUND_STATUS_FAIL = "FAIL";

    private final AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper;
    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;

    TxnRefundCallbackSettler(AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper,
                             AlipayPayTxnDetailMapper alipayPayTxnDetailMapper) {
        this.alipayRefundTxnDetailMapper = alipayRefundTxnDetailMapper;
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
    }

    /**
     * 按商户退款单号收口一次退款回调，语义与 {@link RefundCallbackSettler.Outcome} 逐项对齐
     * （刻意复用那个枚举，避免调用方按两套归宿各写一遍 switch）。
     *
     * @param orderNo       原商户订单号，用于汇总重算与日志
     * @param refundOrderNo 商户退款单号（回调的 {@code outRefundNo}，<b>NEVER 传 {@code refundNo}</b>，
     *                      后者是支付中心侧流水、本表没有它的索引）
     * @param refundResult  支付中心 §5.2 的 {@code refundResult}
     * @param resultMsg     回写进 {@code REMARK} 的描述，取回调的 {@code refundResultDesc}
     */
    public RefundCallbackSettler.Outcome settle(String orderNo, String refundOrderNo, String refundResult,
                                               String resultMsg) {
        if (CALLBACK_RESULT_SUCCESS.equalsIgnoreCase(refundResult)) {
            return settleTerminal(orderNo, refundOrderNo, REFUND_STATUS_SUCCESS, resultMsg,
                    RefundCallbackSettler.Outcome.SETTLED_SUCCESS);
        }
        if (CALLBACK_RESULT_FAIL.equalsIgnoreCase(refundResult)) {
            return settleTerminal(orderNo, refundOrderNo, REFUND_STATUS_FAIL, resultMsg,
                    RefundCallbackSettler.Outcome.SETTLED_FAIL);
        }
        if (CALLBACK_RESULT_PROCESSING.equalsIgnoreCase(refundResult)) {
            log.info("退款回调仍为处理中，本次不回写新表明细, orderNo={}, refundOrderNo={}", orderNo, refundOrderNo);
            return RefundCallbackSettler.Outcome.STILL_PROCESSING;
        }
        log.error("退款回调 refundResult 不在契约取值内，MUST 人工核对, orderNo={}, refundOrderNo={}, refundResult={}",
                orderNo, refundOrderNo, refundResult);
        return RefundCallbackSettler.Outcome.UNKNOWN_RESULT;
    }

    /**
     * CAS 收口 + 仅成功时重算汇总。
     *
     * <p><b>汇总 MUST 在明细已置 {@code SUCCESS} 之后再算</b>：{@code updateRefundSummary} 是按
     * {@code ALIPAY_REFUND_TXN_DETAIL} 现值重算的，早一步算出来的是旧值。
     *
     * <p>汇总影响 0 行只记 ERROR、<b>NEVER 抛异常</b>：钱已经退出去了，把汇总回写变成失败点只会让
     * 本已成功的回调对支付中心报错、引来重推。
     */
    private RefundCallbackSettler.Outcome settleTerminal(String orderNo, String refundOrderNo, String refundStatus,
                                                        String resultMsg, RefundCallbackSettler.Outcome settled) {
        int affected = alipayRefundTxnDetailMapper.settleFromCallback(refundOrderNo, refundStatus, resultMsg);
        if (affected == 0) {
            log.warn("退款回调未命中新表处理中明细，按幂等处理, orderNo={}, refundOrderNo={}, refundStatus={}",
                    orderNo, refundOrderNo, refundStatus);
            return RefundCallbackSettler.Outcome.NOT_MATCHED;
        }
        if (REFUND_STATUS_SUCCESS.equals(refundStatus)) {
            int summary = alipayPayTxnDetailMapper.updateRefundSummary(orderNo);
            if (summary == 0) {
                log.error("退款汇总回写未命中原支付订单，MUST 人工核对 ALIPAY_PAY_TXN_DETAIL 与 ALIPAY_REFUND_TXN_DETAIL, orderNo={}, refundOrderNo={}",
                        orderNo, refundOrderNo);
            }
        }
        log.info("退款回调已收口新表明细, orderNo={}, refundOrderNo={}, refundStatus={}",
                orderNo, refundOrderNo, refundStatus);
        return settled;
    }
}
