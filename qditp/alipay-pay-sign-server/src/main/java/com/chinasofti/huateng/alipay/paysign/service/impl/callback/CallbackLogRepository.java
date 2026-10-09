package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCallbackLogMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 回调方向对 {@code ALIPAY_PAY_CALLBACK_LOG} 的**唯一读写入口**，四条语句全在这里。
 *
 * <p>本表是**追加型事实流水**、刻意没有唯一索引：支付中心每推一次就落一行，行数本身即「推了几次」。
 * 因此 {@code CALLBACK_TYPE} / {@code HANDLE_STATUS} 的全部字面量集中在本类，
 * <b>NEVER 散到编排层</b> —— 那两列的取值由写入者定义，散开就会出现「两处对同一个值理解不同」。
 *
 * <p><b>四个方法一律 catch 全部异常只记日志、NEVER 向上抛</b>：本表只承担留痕与计数，
 * 让它变成失败点等于「留痕失败就把一笔已经扣了款的回调顶回去」，支付中心随后无休止重推。
 * 代价是明确的：落库失败那次拿不到 {@code callbackSeq}（回写自动跳过）、计数失败那次退化成不启用硬限次。
 */
@Component
class CallbackLogRepository {

    private static final Logger log = LoggerFactory.getLogger(CallbackLogRepository.class);

    /** 支付结果回调。 */
    static final String CALLBACK_TYPE_PAY = "PAY";
    /** 退款结果回调。与 {@code PAY} 同列不同值，用于把两个方向分开计数。 */
    private static final String CALLBACK_TYPE_REFUND = "REFUND";

    /** 已落证据、处置未定。 */
    static final String HANDLE_STATUS_PROCESSING = "PROCESSING";
    /** 处置成功。 */
    static final String HANDLE_STATUS_SUCCESS = "SUCCESS";
    /** 本次处置失败，等支付中心重推。 */
    static final String HANDLE_STATUS_FAIL = "FAIL";
    /** 转人工：报文非法，或已达重推上限仍未成功。 */
    static final String HANDLE_STATUS_MANUAL = "MANUAL";

    private static final int HANDLE_MSG_MAX = 500;

    private final AlipayPayCallbackLogMapper alipayPayCallbackLogMapper;

    CallbackLogRepository(AlipayPayCallbackLogMapper alipayPayCallbackLogMapper) {
        this.alipayPayCallbackLogMapper = alipayPayCallbackLogMapper;
    }

    /**
     * 落一行支付回调证据，返回主键；落库失败返回 {@code null}（调用方据此跳过回写）。
     *
     * <p>只落 6 个报文字段 + 处置结果 + 整包原文，<b>刻意不落映射后的支付状态与卡号</b>：
     * 前者是 {@code transStatus} 的派生值（流水表存原文即可，用时再映射），后者主体
     * {@code GATE_TXN_PAY.CARD_ID} 已有；两列已于 2026-09-18 从表里删除，NEVER 加回。
     * {@code RAW_BODY} 是 CLOB，因此**不截断** —— 截断后的报文不再是可举证的原文。
     */
    String recordPayCallback(AlipayTripPayNotifyReqDTO request, String handleStatus, String handleMsg) {
        String callbackSeq = newCallbackSeq();
        try {
            AlipayPayCallbackLog callbackLog = new AlipayPayCallbackLog();
            callbackLog.setCallbackSeq(callbackSeq);
            callbackLog.setOrderNo(request.getOrderNo());
            callbackLog.setCallbackType(CALLBACK_TYPE_PAY);
            callbackLog.setTransStatus(request.getTransStatus());
            callbackLog.setChannelVoucherId(request.getChannelVoucherId());
            callbackLog.setTransAmount(request.getTransAmount());
            callbackLog.setTransTime(request.getTransTime());
            callbackLog.setRawBody(JSON.toJSONString(request));
            callbackLog.setHandleStatus(handleStatus);
            callbackLog.setHandleMsg(truncate(handleMsg));
            callbackLog.setCreateTime(LocalDateTime.now());
            callbackLog.setUpdateTime(LocalDateTime.now());
            alipayPayCallbackLogMapper.insert(callbackLog);
            return callbackSeq;
        } catch (Exception e) {
            log.error("支付宝支付回调凭据落库失败，本次处理继续, orderNo={}", request.getOrderNo(), e);
            return null;
        }
    }

    /**
     * 落一行退款回调证据，返回主键；落库失败返回 {@code null}（调用方据此跳过回写）。
     *
     * <p>与支付方向同形：<b>先落 {@code PROCESSING} 证据、再做业务收口</b>，处置结果随后按主键回写。
     * NEVER 退回成「落库时就写终态」——那时收口还没做，写出来的处置状态是猜的。
     *
     * <p>不对两个字符串列做截断：{@code REFUND_ORDER_NO} 是我方生成的号（64 字符）、{@code REFUND_STATUS}
     * 是枚举值（16 字符），都在列长内；万一对端送超长值，Oracle 报 {@code ORA-12899} 会被本方法的 catch
     * 兜住，代价只是丢这一行证据 + 一条 ERROR 日志，不影响回调收口。
     */
    String recordRefundCallback(AlipayTripRefundNotifyReqDTO request, RefundNotifyCommand.Accepted command,
                                String handleStatus, String handleMsg) {
        String callbackSeq = newCallbackSeq();
        try {
            AlipayPayCallbackLog callbackLog = new AlipayPayCallbackLog();
            callbackLog.setCallbackSeq(callbackSeq);
            callbackLog.setOrderNo(command.orderNo());
            callbackLog.setCallbackType(CALLBACK_TYPE_REFUND);
            callbackLog.setRefundOrderNo(command.refundOrderNo());
            callbackLog.setRefundAmount(command.refundAmount());
            callbackLog.setRefundStatus(command.refundResult());
            callbackLog.setRawBody(JSON.toJSONString(request));
            callbackLog.setHandleStatus(handleStatus);
            callbackLog.setHandleMsg(truncate(handleMsg));
            callbackLog.setCreateTime(LocalDateTime.now());
            callbackLog.setUpdateTime(LocalDateTime.now());
            alipayPayCallbackLogMapper.insert(callbackLog);
            return callbackSeq;
        } catch (Exception e) {
            log.error("支付宝退款回调凭据落库失败，本次处理继续, orderNo={}", command.orderNo(), e);
            return null;
        }
    }


    /**
     * 统计该订单 {@code PAY} 回调的累计推送次数（含本次）。
     *
     * <p>计数失败返回 {@code 0}，等于**本次不启用硬限次** —— 宁可多让支付中心推一轮，
     * 也不能因为一条 COUNT 失败就提前放弃一笔真实回调。
     */
    int countPayPush(String orderNo) {
        try {
            return alipayPayCallbackLogMapper.countByOrderNo(orderNo, CALLBACK_TYPE_PAY);
        } catch (Exception e) {
            log.error("统计支付回调推送次数失败，本次不启用硬限次, orderNo={}", orderNo, e);
            return 0;
        }
    }

    /** 按主键回写处置结果；{@code callbackSeq} 为 null（证据没落成）时直接跳过。 */
    void updateHandleResult(String callbackSeq, String handleStatus, String handleMsg) {
        if (callbackSeq == null) {
            return;
        }
        try {
            alipayPayCallbackLogMapper.updateHandleResult(callbackSeq, handleStatus, truncate(handleMsg));
        } catch (Exception e) {
            log.error("回写支付回调处理结果失败, callbackSeq={}, handleStatus={}", callbackSeq, handleStatus, e);
        }
    }

    /** 把该订单最后一条 {@code PAY} 回调置 {@code MANUAL}，供运维巡检捞出来。 */
    void markPayManual(String orderNo, String reason) {
        try {
            alipayPayCallbackLogMapper.markManualByOrderNo(orderNo, CALLBACK_TYPE_PAY, truncate(reason));
        } catch (Exception e) {
            log.error("标记支付回调需人工处理失败, orderNo={}", orderNo, e);
        }
    }

    private String newCallbackSeq() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= HANDLE_MSG_MAX ? value : value.substring(0, HANDLE_MSG_MAX);
    }
}
