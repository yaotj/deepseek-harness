package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    /**
     * {@code TRANS_TIME} 入库长度：14 位 {@code yyyyMMddHHmmss}。
     *
     * <p>全仓对外口径就是这个：IF8A-05 / IF8A-34 的 {@code payOrderNoDate}
     * （{@code docs/business/ride-code.md:351} 与 {@code :679}）、日票回填那条的
     * {@code SimpleDateFormat("yyyyMMddHHmmss")}（{@code docs/business/daily-ticket.md:417}），
     * 以及同一份应答里的 {@code entryDate} / {@code exitDate}。
     */
    private static final int TRANS_TIME_LENGTH = 14;

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
     * @param transTime 报文里的支付时刻**原文**，本方法负责把它归一成 14 位后再入库，见
     *                  {@link #normalizeTransTime(String)}。原文本身不会丢：
     *                  {@code ALIPAY_PAY_CALLBACK_LOG} 的 {@code TRANS_TIME} 与 {@code RAW_BODY}
     *                  都存着，那张表才是回调台账。**NEVER 反过来把回调台账也归一** —— 举证要原文。
     */
    void applyCallback(String orderNo, String payStatus, String channelVoucherId, String transTime) {
        String normalizedTransTime = normalizeTransTime(transTime);
        AlipayPayTxnDetail update = new AlipayPayTxnDetail();
        update.setOrderNo(orderNo);
        update.setPayStatus(payStatus);
        update.setChannelOrderNo(channelVoucherId);
        update.setTransTime(normalizedTransTime);
        try {
            int affected = alipayPayTxnDetailMapper.updatePayCallback(update);
            if (affected == 0) {
                log.warn("支付回调回写支付明细影响 0 行（本单已是终态或本表无此明细行），不补建、不报错, orderNo={}, payStatus={}",
                        orderNo, payStatus);
                return;
            }
            log.info("支付回调已回写支付明细, orderNo={}, payStatus={}, channelOrderNo={}, transTime={}, rawTransTime={}",
                    orderNo, payStatus, channelVoucherId, normalizedTransTime, transTime);
        } catch (RuntimeException e) {
            log.error("支付回调回写支付明细异常，已忽略以免引来支付中心重推，MUST 人工核对本单支付状态, orderNo={}, payStatus={}",
                    orderNo, payStatus, e);
        }
    }

    /**
     * 把回调报文的 {@code transTime} 归一成 14 位 {@code yyyyMMddHHmmss} 再入库。
     *
     * <p><b>这条口径 2026-09-20 起翻转过一次，NEVER 回退成「原文直存」</b>：此前本列存的是报文原文、
     * 由查询侧（{@code trans-query-server} 的 {@code AlipayTravelQueryHandler.normalizePayOrderNoDate}）
     * 临时归一。实测同一列里同时出现 19 位 {@code 2026-09-18 16:44:41} 与 14 位 {@code 20260918021500}
     * 两种形态 —— 列本身不统一，于是**每一个新的读取方都得自己再归一一次**，漏一处就把 19 位原样发给
     * 对外契约。按用户 2026-09-20 的裁决改为**在唯一写入口归一**，列值自此只有一种格式。
     *
     * <p>规则与查询侧那份逐字一致（剥非数字 → 取前 14 位 → 不足 14 位原样 + WARN）。两处**有意保留**、
     * NEVER 合并成公共工具类：写入侧保证新数据，查询侧兜住 2026-09-18 16:15:06 加列时刻之前、
     * 以及本次归一之前落库的旧行；跨模块抽公共类只能放 {@code model}，而这是内部实现细节、不是对外契约。
     *
     * <p>三条 NEVER：**NEVER 补零**（月日被补成 {@code 01} 会造出看着合法的假时间）、
     * **NEVER 按时间戳换算**（库里出现过 13 位毫秒串，但没有证据说明它与那些 19 位字符串同源）、
     * **NEVER 用 {@code SimpleDateFormat.parse} / {@code TO_DATE} 解析**（格式不统一，解析即抛）。
     * 不足 14 位时原样入库而不是丢弃：那仍是对端给过的事实，丢掉等于制造 null。
     */
    private String normalizeTransTime(String transTime) {
        if (!StringUtils.hasText(transTime)) {
            return null;
        }
        String digits = transTime.replaceAll("\\D", "");
        if (digits.length() >= TRANS_TIME_LENGTH) {
            return digits.substring(0, TRANS_TIME_LENGTH);
        }
        log.warn("支付回调的 transTime 无法归一成 {} 位，原样入库, transTime={}", TRANS_TIME_LENGTH, transTime);
        return transTime;
    }
}
