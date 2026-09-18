package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundLogMapper;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 退款申请方向对 {@code ALIPAY_REFUND_LOG} 的**唯一写入口**，并连带持有由它派生的退款汇总回写。
 *
 * <p><b>为什么汇总（{@code ALIPAY_PAY_LOG.updateRefundSummary}）也在这里</b>：那条 SQL 是
 * **按 {@code ALIPAY_REFUND_LOG} 重算**已退金额与退款状态的，它的正确性完全取决于本表刚写成什么。
 * 放到编排层就会出现「明细已 SUCCESS、汇总还没刷」这类只能靠读代码才能发现的时序，
 * 因此 MUST 与明细写入同一个持有者。<b>但 {@code ALIPAY_PAY_LOG} 的其余写语句
 * （{@code updatePayStatus} / {@code updatePayNotify} / {@code updatePayQueryResultIfNotSuccess}）
 * NEVER 进本类</b> —— 那些是支付方向的状态推进，不归退款链路。
 *
 * <p>状态字面量集中在本类：本表的状态值由写入者定义，散到编排层会出现「两处对同一个值的理解不同」。
 */
@Component
class RefundLogRepository {

    private static final Logger log = LoggerFactory.getLogger(RefundLogRepository.class);

    /** 出网前落的状态，业务应答到达后收口成 SUCCESS / FAIL。 */
    static final String REFUND_STATUS_PROCESSING = "PROCESSING";
    /** 退款成功，终态。 */
    static final String REFUND_STATUS_SUCCESS = "SUCCESS";
    /** 退款失败，终态。 */
    static final String REFUND_STATUS_FAIL = "FAIL";
    /** 结果未定时的响应码占位，NEVER 拿它当成功码。 */
    static final String RESULT_CODE_INIT = "INIT";

    /** 支付宝渠道的卡机构编号，落库与出网报文都用它。 */
    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";

    private final AlipayRefundLogMapper alipayRefundLogMapper;
    private final AlipayPayLogMapper alipayPayLogMapper;

    RefundLogRepository(AlipayRefundLogMapper alipayRefundLogMapper, AlipayPayLogMapper alipayPayLogMapper) {
        this.alipayRefundLogMapper = alipayRefundLogMapper;
        this.alipayPayLogMapper = alipayPayLogMapper;
    }

    /** 同一原订单下未收口（{@code PROCESSING}）的退款明细条数，供幂等短路用。 */
    int countProcessing(String orderNo) {
        return alipayRefundLogMapper.countByOrderNoAndStatus(orderNo, REFUND_STATUS_PROCESSING);
    }

    /**
     * 开一次退款尝试：落一行 {@code PROCESSING} 明细并提交，返回其 {@code REFUND_SEQ}。
     *
     * <p><b>顺序 MUST 是「落库并提交 → 出网」</b>：先留痕才有失败可核对、对账有源。调用方 NEVER 把
     * 这一步与出网包进同一个事务。
     *
     * <p>并发下第二条 INSERT 会撞 {@code UK_ARL_REFUND_ORDER_NO}。这里的处置与扣费方向<b>刻意不同</b>：
     * 扣费方向撞唯一索引是「同一 {@code orderNo} 已有人落好」，可以按幂等继续；
     * 退款方向的 {@code refundOrderNo} 是**本次现生成的**，撞上只能是重复提交 ——
     * 继续下去等于对同一笔原支付发两次退款，<b>MUST 拒绝、NEVER 按幂等放行</b>。
     *
     * <p>判定 MUST 沿 {@code getCause()} 链走，NEVER 只 catch 最外层的 {@code DuplicateKeyException}
     * —— 本模块开了 tracing，观测切面会把异常重新包一层，只认最外层类名的写法会静默失效。
     */
    String openRefund(RefundCommand command, AlipayPayLog payLog, String channelAgreementNo,
                      String refundAmount, String refundOrderNo, String requestBody) {
        AlipayRefundLog refundLog = new AlipayRefundLog();
        refundLog.setRefundSeq(UUID.randomUUID().toString().replaceAll("-", ""));
        refundLog.setThirdUserId(payLog.getThirdUserId());
        refundLog.setCardId(payLog.getCardId());
        refundLog.setOrderNo(command.orderNo());
        refundLog.setRefundAmount(refundAmount);
        refundLog.setRefundStatus(REFUND_STATUS_PROCESSING);
        refundLog.setCardIssueCode(CARD_ISSUE_CODE_ALIPAY);
        refundLog.setChannelAgreementNo(channelAgreementNo);
        refundLog.setRefundOrderNo(refundOrderNo);
        refundLog.setRequestBody(requestBody);
        refundLog.setResponseBody("");
        refundLog.setResultCode(RESULT_CODE_INIT);
        refundLog.setResultMsg("退款处理中");
        refundLog.setDeleteFlag("0");
        refundLog.setVersion("1");
        LocalDateTime now = LocalDateTime.now();
        refundLog.setCreateTime(now);
        refundLog.setUpdateTime(now);
        try {
            alipayRefundLogMapper.insert(refundLog);
        } catch (RuntimeException e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            log.warn("退款明细插入命中唯一索引，判定为重复提交, orderNo={}, refundOrderNo={}",
                    command.orderNo(), refundOrderNo);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请重复提交");
        }
        return refundLog.getRefundSeq();
    }

    /**
     * 回写退款明细的状态与响应体。
     *
     * <p>响应体原文一律落库留证 —— 支付中心的 {@code retCode} 码表我方没有，出问题时只能靠原文复核。
     */
    void writeResult(String refundSeq, String refundStatus, String resultCode, String resultMsg, String rawBody) {
        alipayRefundLogMapper.updateRefundStatus(refundSeq, refundStatus, resultCode, resultMsg,
                rawBody, LocalDateTime.now());
    }

    /**
     * 按 {@code ALIPAY_REFUND_LOG} 重算原支付订单的已退金额与退款状态。
     *
     * <p><b>MUST 在明细已置为 {@code SUCCESS} 之后调</b>：SQL 是按本表现值重算的，早调一步算出来的是旧值。
     *
     * <p>影响 0 行只记 ERROR、<b>NEVER 抛异常</b>：钱已经退出去了，把汇总回写变成失败点只会让这笔
     * 已成功的退款对上游报错、引来重复提交。
     */
    void refreshSummary(String orderNo, String refundOrderNo) {
        int affected = alipayPayLogMapper.updateRefundSummary(orderNo);
        if (affected == 0) {
            log.error("退款汇总回写未命中原支付订单，MUST 人工核对 ALIPAY_PAY_LOG 与 ALIPAY_REFUND_LOG, orderNo={}, refundOrderNo={}",
                    orderNo, refundOrderNo);
        }
    }

    /** 沿 cause 链判完整性冲突，理由见 {@link #openRefund} 的注释。 */
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
