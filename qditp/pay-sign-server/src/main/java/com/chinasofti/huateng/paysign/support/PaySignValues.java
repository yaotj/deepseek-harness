package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 签约 / 支付 / 回调三条链路共用的**纯值转换**函数集合。 */
public final class PaySignValues {

    private static final Logger log = LoggerFactory.getLogger(PaySignValues.class);

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private PaySignValues() {
    }

    /** 将 ITP 侧的 paymentVendor 归一化。 */
    public static String normalizeVendor(String paymentVendor) {
        if (!StringUtils.hasText(paymentVendor)) {
            return null;
        }
        String code = paymentVendor.trim();
        if (!PaymentVendorEnum.isValid(code)) {
            log.warn("未知的支付渠道编码: {}", code);
        }
        return code;
    }

    /** 解析支付平台返回的 yyyyMMddHHmmss 时间格式。解析失败返回 null，不抛异常。 */
    public static LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATETIME_FORMATTER);
        } catch (Exception e) {
            log.warn("解析时间失败, value={}", value);
            return null;
        }
    }

    public static LocalDateTime parseDateTime(String value, LocalDateTime defaultValue) {
        LocalDateTime parsed = parseDateTime(value);
        return parsed == null ? defaultValue : parsed;
    }

    /** 从对象中安全提取字符串值。 */
    public static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    /** 字符串为空时返回默认值。 */
    public static String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /** 把支付平台的支付状态归一成本模块落库用的取值。 */
    public static String convertPayStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "PROCESSING";
        }
        String normalized = status.trim().toUpperCase();
        if ("SUCCESS".equals(normalized) || "PAID".equals(normalized)) {
            return "SUCCESS";
        }
        if ("FAIL".equals(normalized) || "FAILED".equals(normalized)) {
            return "FAIL";
        }
        return normalized;
    }

    public static String resolveTxnDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /** 优先采用发起方透传的交易日期，为空时才回落到本地当日。 */
    public static String resolveTxnDate(String requestTxnDate) {
        if (requestTxnDate != null && !requestTxnDate.isBlank()) {
            return requestTxnDate.trim();
        }
        String fallback = resolveTxnDate();
        log.warn("请求未带 txnDate，回落到本地当日 {}；上游若为 gate-txn-pay 说明其镜像未重建", fallback);
        return fallback;
    }
}
