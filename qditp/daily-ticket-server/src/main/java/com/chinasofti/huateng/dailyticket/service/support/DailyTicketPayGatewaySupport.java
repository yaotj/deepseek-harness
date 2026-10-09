package com.chinasofti.huateng.dailyticket.service.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class DailyTicketPayGatewaySupport {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketPayGatewaySupport.class);

    private DailyTicketPayGatewaySupport() {
    }

    public static boolean isPaySuccessStatus(String status) {
        return "SUCCESS".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status)
                || "PAY_SUCCESS".equalsIgnoreCase(status) || "TRADE_SUCCESS".equalsIgnoreCase(status)
                || "1".equals(status);
    }

    public static boolean isPayFailedStatus(String status) {
        return "FAIL".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)
                || "PAY_FAILED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status) || "0".equals(status);
    }

    public static Date parseGatewayPayDate(String payDate) {
        if (!StringUtils.hasText(payDate)) {
            return new Date();
        }
        try {
            return new SimpleDateFormat("yyyyMMddHHmmss").parse(payDate);
        } catch (Exception e) {
            log.warn("日票支付查询返回的支付时间格式错误 payDate={}", payDate);
            return new Date();
        }
    }

    public static Integer integerValue(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
