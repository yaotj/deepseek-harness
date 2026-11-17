package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;


sealed interface RefundNotifyCommand {

    Logger LOG = LoggerFactory.getLogger(RefundNotifyCommand.class);


    record Accepted(String orderNo,
                    String refundOrderNo,
                    Integer refundAmount,
                    String refundResult) implements RefundNotifyCommand {
    }

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
