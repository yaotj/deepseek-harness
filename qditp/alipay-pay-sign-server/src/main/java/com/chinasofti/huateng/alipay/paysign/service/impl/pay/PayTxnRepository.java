package com.chinasofti.huateng.alipay.paysign.service.impl.pay;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 扣费申请方向对 {@code ALIPAY_PAY_TXN_DETAIL} 的**唯一写入口**。
 *
 * <p>本表有 5 条写语句，按方向分给三个写入者，**NEVER 越界**：
 * <ul>
 *   <li>本类（扣费申请方向）：{@code insert} + {@code markRequesting} + {@code updateRequestResult}；</li>
 *   <li>支付回调 / 支付结果查询：{@code updatePayCallback} / {@code updatePayQueryResultIfNotSuccess}
 *       —— 那两条带状态白名单，是回调与查询各自的幂等出口，本类 NEVER 调；</li>
 *   <li>退款汇总：{@code updateRefundSummary} —— 归退款链路。</li>
 * </ul>
 * 把这三组混进一个类，就再也说不清「这笔单子的状态是谁推进的」。
 *
 * <p>状态字面量集中在本类：本表的状态值由写入者定义，散到编排层会出现「两处对同一个值的理解不同」。
 */
@Component
class PayTxnRepository {

    private static final Logger log = LoggerFactory.getLogger(PayTxnRepository.class);

    /** 出网前的初始状态，仅 {@link #openAttempt} 用。 */
    private static final String STATUS_INIT = "INIT";
    /** 已受理、结果未定。{@code markRequesting} 会把状态压回它。 */
    static final String STATUS_PROCESSING = "PROCESSING";
    /** 扣款成功，终态。 */
    static final String STATUS_SUCCESS = "SUCCESS";
    /** 扣款失败，终态。 */
    static final String STATUS_FAIL = "FAIL";

    private static final String REFUND_STATUS_NONE = "NONE";

    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;

    PayTxnRepository(AlipayPayTxnDetailMapper alipayPayTxnDetailMapper) {
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
    }

    AlipayPayTxnDetail findByOrderNo(String orderNo) {
        return alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
    }

    /**
     * 本单是否已收口成功。
     *
     * <p>只认 {@code SUCCESS} 这一个终态，**NEVER 扩成「非 PROCESSING 即终态」** ——
     * {@code FAIL} 单允许上游按同一 {@code orderNo} 重推（换个渠道状态可能就成了），
     * 把它一并短路等于永久拒绝这笔单子。
     */
    static boolean isSettled(AlipayPayTxnDetail row) {
        return row != null && STATUS_SUCCESS.equals(row.getPayStatus());
    }

    /**
     * 开一次扣款尝试：缺行就落一行 {@code INIT}，随后把状态压回 {@code PROCESSING} 并把请求次数 +1。
     *
     * <p><b>顺序 MUST 是「落库并提交 → 出网」</b>：先留痕才有失败可补偿、对账有源。调用方 NEVER 把
     * 这一步与出网包进同一个事务。
     *
     * @param existing 调用方已读到的现存明细，{@code null} 表示本单还没有明细行
     */
    void openAttempt(PayCommand command, AlipaySignInfo signInfo, String txnDate, AlipayPayTxnDetail existing) {
        if (existing == null) {
            insert(command, signInfo, txnDate);
        }
        alipayPayTxnDetailMapper.markRequesting(command.orderNo());
    }

    /**
     * 回写支付中心的同步应答（受理结果）。
     *
     * <p>影响 0 行只记 ERROR、**NEVER 抛异常**：这一步是留痕，把它变成失败点会让一笔已经打给支付中心
     * 的扣款对上游报错、引来重推。
     */
    void writeRequestResult(String orderNo, String payStatus, String payCenterOrderNo, String channelOrderNo) {
        AlipayPayTxnDetail update = new AlipayPayTxnDetail();
        update.setOrderNo(orderNo);
        update.setPayStatus(payStatus);
        update.setPayCenterOrderNo(payCenterOrderNo);
        update.setChannelOrderNo(channelOrderNo);
        int affected = alipayPayTxnDetailMapper.updateRequestResult(update);
        if (affected == 0) {
            log.error("回写支付申请结果影响 0 行，明细行可能不存在、MUST 人工核对, orderNo={}, payStatus={}", orderNo, payStatus);
        }
    }

    /**
     * 落一行支付明细，状态 {@code INIT}。
     *
     * <p>并发下两条请求可能都没读到明细行，第二条 INSERT 会撞 {@code UK_APTD_ORDER}。那不是错误、
     * 是幂等生效：本方法把它当「已有别人落好了」继续。
     * <b>判定 MUST 沿 {@code getCause()} 链走</b>，NEVER 只 catch 最外层的 {@code DuplicateKeyException}
     * —— 本模块开了 tracing，观测切面会把异常重新包一层，只认最外层类名的写法会静默失效。
     */
    private void insert(PayCommand command, AlipaySignInfo signInfo, String txnDate) {
        AlipayPayTxnDetail record = new AlipayPayTxnDetail();
        record.setOrderNo(command.orderNo());
        record.setTxnDate(txnDate);
        record.setPayStatus(STATUS_INIT);
        record.setAmount(command.amount());
        record.setRefundStatus(REFUND_STATUS_NONE);
        record.setRefundAmount(0);
        record.setRequestSignSeq(signInfo.getAgreementCode());
        record.setChannelAgreementNo(signInfo.getChannelAgreementCode());
        record.setRequestCount(0);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            alipayPayTxnDetailMapper.insert(record);
        } catch (RuntimeException e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            log.info("支付明细已被并发请求落好，按幂等继续, orderNo={}", command.orderNo());
        }
    }

    /** 沿 cause 链判完整性冲突，理由见 {@link #insert} 的注释。 */
    private boolean isIntegrityViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name.contains("DuplicateKey") || name.contains("IntegrityConstraintViolation")) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }
}
