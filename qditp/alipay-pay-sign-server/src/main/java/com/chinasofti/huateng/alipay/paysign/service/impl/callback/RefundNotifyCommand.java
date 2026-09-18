package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 一次退款结果回调的解析结果，形态与 {@link PayNotifyCommand} 对齐但**只有两支** ——
 * 退款方向没有状态白名单（{@code refundResult} 原值直接落库留证、不做映射），因此没有
 * 「非法取值」那一支。
 *
 * <p><b>{@code refundOrderNo} 取 {@code outRefundNo}、NEVER 取 {@code refundNo}</b>：
 * 前者是商户退款流水号、与 {@code ALIPAY_REFUND_LOG.REFUND_ORDER_NO} 同源；后者是支付中心侧自己的号，
 * 拿它关联会串单，它只在 {@code RAW_BODY} 里留证。
 */
sealed interface RefundNotifyCommand {

    Logger LOG = LoggerFactory.getLogger(RefundNotifyCommand.class);

    /**
     * 报文合法。
     *
     * @param refundOrderNo 商户退款流水号（取自 {@code outRefundNo}）
     * @param refundAmount  已解析成分的退款金额；解析不出时为 {@code null}
     * @param refundResult  对端原值，不做映射
     */
    record Accepted(String orderNo,
                    String refundOrderNo,
                    Integer refundAmount,
                    String refundResult) implements RefundNotifyCommand {
    }

    /** 报文为空或 {@code orderNo} 为空：**不落凭据**，直接拒。 */
    record MissingOrderNo() implements RefundNotifyCommand {
    }

    static RefundNotifyCommand from(AlipayTripRefundNotifyReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return new MissingOrderNo();
        }
        return new Accepted(request.getOrderNo(), request.getOutRefundNo(),
                parseRefundAmount(request.getRefundAmount(), request.getOrderNo()),
                request.getRefundResult());
    }

    /**
     * 把契约 §5.2 的 {@code refundAmount}（字符串、单位分）解析成分。
     *
     * <p>解析不出时返回 {@code null} 让该列留空，<b>NEVER 抛异常</b> —— 一个金额解析失败不该把整行证据
     * 连带丢掉，原文在 {@code RAW_BODY} 里可复核。
     */
    private static Integer parseRefundAmount(String refundAmount, String orderNo) {
        if (!StringUtils.hasText(refundAmount)) {
            return null;
        }
        try {
            return Integer.valueOf(refundAmount.trim());
        } catch (NumberFormatException e) {
            LOG.warn("支付宝退款回调的退款金额不是整数分，本列留空、原文已进 RAW_BODY, orderNo={}, refundAmount={}",
                    orderNo, refundAmount);
            return null;
        }
    }
}
