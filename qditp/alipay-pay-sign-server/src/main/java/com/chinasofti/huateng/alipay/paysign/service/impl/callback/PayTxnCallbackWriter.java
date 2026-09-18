package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 支付回调方向对 {@code ALIPAY_PAY_TXN_DETAIL} 的**唯一写入口**。
 *
 * <p>为什么另起一个类、而不是加到 {@code PayTxnRepository}：那个类的注释已经把本表 5 条写语句按方向
 * 分给三个写入者，并明确「{@code updatePayCallback} 带状态白名单，是回调的幂等出口，本类 NEVER 调」。
 * 回调方向自己持有这一条，才说得清「这笔单的状态是谁推进的」。
 *
 * <p><b>本类 NEVER 抛异常</b>：回调链路的任何一步失败都会让本已被支付中心确认的结果对上游报错、
 * 引来重推，而重推解决不了「本地 UPDATE 影响 0 行」这类问题。影响 0 行有两种含义且都不是错误：
 * ① 本单已是 {@code SUCCESS} 终态（白名单排除它）= 重推的幂等命中；② 本单在本表没有明细行 ——
 * {@code requestPay} 没走到落库就失败时会这样，此时回调仍会来，**只记 WARN、NEVER 补建行**
 * （凭什么金额、凭什么协议号建？补出来的行是伪造的支付事实）。
 */
@Component
class PayTxnCallbackWriter {

    private static final Logger log = LoggerFactory.getLogger(PayTxnCallbackWriter.class);

    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;

    PayTxnCallbackWriter(AlipayPayTxnDetailMapper alipayPayTxnDetailMapper) {
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
    }

    /**
     * 按回调结果推进本表的支付状态。
     *
     * <p>{@code payCenterOrderNo} 刻意不传：支付结果回调报文里没有这个字段，而 SQL 对它用了
     * {@code NVL(入参, 原值)}，传 null 即保留 {@code requestPay} 阶段回写的值。
     * **NEVER 为了「填满字段」把 channelVoucherId 塞给它** —— 那是渠道流水号、不是支付中心订单号。
     *
     * @param payStatus {@link PayNotifyCommand.Accepted#payStatus()} 映射后的本地口径（{@code SUCCESS} / {@code FAIL}）
     * @param transTime 报文里的支付时刻原文，**NEVER 在这里解析或格式化** —— 实测格式不统一（既有
     *                  {@code 2026-09-18 15:49:30} 也有 {@code 20260918021500}），列是 VARCHAR2 且
     *                  SQL 侧套了 NVL，传 null 即保留已有值。支付宝出行记录应答的 {@code payOrderNoDate}
     *                  取的就是这一列，旧实现取的是 {@code ALIPAY_PAY_LOG.TRANS_TIME}、同一个值。
     */
    void applyCallback(String orderNo, String payStatus, String channelVoucherId, String transTime) {
        AlipayPayTxnDetail update = new AlipayPayTxnDetail();
        update.setOrderNo(orderNo);
        update.setPayStatus(payStatus);
        update.setChannelOrderNo(channelVoucherId);
        update.setTransTime(transTime);
        try {
            int affected = alipayPayTxnDetailMapper.updatePayCallback(update);
            if (affected == 0) {
                log.warn("支付回调回写支付明细影响 0 行（本单已是终态或本表无此明细行），不补建、不报错, orderNo={}, payStatus={}",
                        orderNo, payStatus);
                return;
            }
            log.info("支付回调已回写支付明细, orderNo={}, payStatus={}, channelOrderNo={}, transTime={}",
                    orderNo, payStatus, channelVoucherId, transTime);
        } catch (RuntimeException e) {
            log.error("支付回调回写支付明细异常，已忽略以免引来支付中心重推，MUST 人工核对本单支付状态, orderNo={}, payStatus={}",
                    orderNo, payStatus, e);
        }
    }
}
