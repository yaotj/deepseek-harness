package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 签约 / 支付 / 回调三条链路共用的**纯值转换**函数集合。
 *
 * <p>本类是对 AGENTS.md §5.1「NEVER 主动创建新的工具类」的**有意破例**，理由与
 * {@code face-pay-server} 的 {@code F2fDuplicateKey}（ADR-D84）同型：这几个方法原本是
 * {@code PaySignWorkflow} 的私有方法，而该类正在按「支付组 / 回调组 / 签约+解约组」拆成
 * 三个领域服务 —— 三个组都要用它们，不抽出来就等于**在三个类里各留一份逐字副本**，
 * 日后任一处改动（尤其 {@code convertPayStatus} 的取值映射）都会静默分叉。
 *
 * <p><b>准入判据</b>：只收「无字段依赖、无 IO、给同样输入必得同样输出」的函数。
 * 因此 {@code resolveNotifyUrl} / {@code resolvePayNotifyUrl} 都**不在**本类里 ——
 * 它们要读 {@code PaySignProperties}，且签约与支付两条链路的回落顺序本就不同；
 * 同理 {@code PaySignGatewayMessages} 也是靠把 notifyUrl 当参数传入来保持纯函数。
 *
 * <p><b>NEVER 往本类里塞需要注入 Bean 的方法</b>：一旦要注入，本类就得变成 Spring Bean，
 * 三个领域服务对它的静态导入全部作废、拆分收益归零。
 */
public final class PaySignValues {

    private static final Logger log = LoggerFactory.getLogger(PaySignValues.class);

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private PaySignValues() {
    }

    /**
     * 将 ITP 侧的 paymentVendor 归一化。
     *
     * <p>未知编码只记 warn、**照样原样返回**，NEVER 改成抛异常或返 null —— 支付平台侧可能
     * 先于本枚举支持新渠道，拒绝会把能跑通的签约打死。
     */
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

    /**
     * 解析支付平台返回的 yyyyMMddHHmmss 时间格式。解析失败返回 null，不抛异常。
     */
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

    /**
     * 从对象中安全提取字符串值。
     */
    public static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    /**
     * 字符串为空时返回默认值。
     */
    public static String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 把支付平台的支付状态归一成本模块落库用的取值。
     *
     * <p>返回值直接写进 {@code PAY_TXN_DETAIL.PAY_STATUS}，而全模块是按 String 字面量比较状态的
     * （AGENTS.md §2.2.1），**改这里的任何一个返回值 MUST 全局 grep 所有比较点**。
     * 末行故意「认不出就原样返回大写」而不是回落 PROCESSING —— 那样会把未知终态伪装成中间态、
     * 引来无休止重推。
     */
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

    /**
     * 优先采用发起方透传的交易日期，为空时才回落到本地当日。
     *
     * <p>仅 PAY_TXN_DETAIL 用这个重载：它与行程侧 GATE_TXN_PAY 按 (ORDER_NO, TXN_DATE)
     * 一一对应且同以该列做月分区，取本地当日会在跨零点时分叉。PAY_REFUND_DETAIL 与
     * PAY_CALLBACK_LOG 是各自独立的事件、日期就该是它们自己发生的日期，
     * **NEVER 把这个重载套到那两处**。
     */
    public static String resolveTxnDate(String requestTxnDate) {
        if (requestTxnDate != null && !requestTxnDate.isBlank()) {
            return requestTxnDate.trim();
        }
        String fallback = resolveTxnDate();
        log.warn("请求未带 txnDate，回落到本地当日 {}；上游若为 gate-txn-pay 说明其镜像未重建", fallback);
        return fallback;
    }
}
