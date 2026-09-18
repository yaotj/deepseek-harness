package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Component
public class RefundAmountCalculator {
    private static final Logger log = LoggerFactory.getLogger(RefundAmountCalculator.class);

    public String resolveRefundAmount(String requestRefundAmount, AlipayPayLog payLog) {
        if (!StringUtils.hasText(requestRefundAmount)) {
            BigDecimal paid = parseAmount(payLog.getPayAmount());
            BigDecimal refunded = payLog.getRefundAmount() != null ? parseAmount(payLog.getRefundAmount()) : BigDecimal.ZERO;
            BigDecimal available = paid.subtract(refunded);
            if (available.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("退款金额异常：历史退款金额大于原订单金额, orderNo=" + payLog.getOrderNo());
            }
            return available.toPlainString();
        }
        return requestRefundAmount;
    }

    public boolean validateRefundAmount(String refundAmount, AlipayPayLog payLog) {
        BigDecimal paid = parseAmount(payLog.getPayAmount());
        BigDecimal refunded = payLog.getRefundAmount() != null ? parseAmount(payLog.getRefundAmount()) : BigDecimal.ZERO;
        BigDecimal available = paid.subtract(refunded);
        BigDecimal current = parseAmount(refundAmount);

        if (current.compareTo(available) > 0) {
            throw new IllegalArgumentException("退款金额超出可退金额");
        }
        if (current.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退款金额必须大于0");
        }
        return true;
    }

    public BigDecimal parseAmount(String amount) {
        return doParseAmount(amount);
    }

    private BigDecimal doParseAmount(String amount) {
        if (amount == null || amount.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            log.error("退款金额格式异常, amount={}", amount, e);
            throw new IllegalArgumentException("退款金额格式异常: " + amount);
        }
    }
}
