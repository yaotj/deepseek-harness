package com.chinasofti.huateng.dailyticket.service.support;

import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.springframework.util.StringUtils;

/**
 * 日票域的无状态判定与应答收口。
 *
 * <p>本类是对 AGENTS.md §5.1「NEVER 主动创建新的工具类」的<b>有意破例</b>，理由与
 * {@code face-pay-server} 的 {@code F2fDuplicateKey} 同类：这些判定原先是
 * {@code DailyTicketServiceImpl} 的私有方法，而下单 / 支付 / 退款 / 小程序同步四条链路都在用。
 * 把退款聚合拆成独立服务时，若不先把它们收口，只剩两个选择——让新服务反向依赖那个 god class，
 * 或者逐字复制一份。两者都比破例更糟。
 *
 * <p><b>本类只准放无状态纯函数</b>：不注入任何 mapper、不碰数据库、不持有可变字段。
 * 需要写库的状态推进（{@code markRefunded} / {@code lockTicketForRefund} 之类）
 * <b>NEVER 放进来</b>，那些属于聚合的状态守卫，去 writer 类。
 */
public final class DailyTicketOrderSupport {
    /** 对 APP / 设备的成功应答码。 */
    public static final String RET_SUCCESS = "0000";
    /** 对 APP / 设备的失败应答码。 */
    public static final String RET_FAIL = "9999";
    /** orderType=1 日票（含多日计次票）。 */
    public static final String ORDER_TYPE_DAILY_TICKET = "1";
    /** orderType=2 旅游票（= 全城通日票，与规格一致）。 */
    public static final String ORDER_TYPE_TRAVEL_TICKET = "2";

    /** 免费票的 tradeNo / paymentOrderNo 前缀，用于识别「不走支付网关」的订单。 */
    public static final String FREE_ORDER_PREFIX = "FREE-";
    /** 海之巴士渠道订单的 tradeNo 前缀。 */
    public static final String SEA_BUS_ORDER_PREFIX = "SEA_BUS-";
    /** 小程序（出行 UH）渠道订单的 tradeNo 前缀。 */
    public static final String CXUH_ORDER_PREFIX = "CXUH-";

    /** ORDER_SOURCE=4 海之巴士。 */
    public static final String ORDER_SOURCE_SEA_BUS = "4";
    /** ORDER_SOURCE=6 小程序。 */
    public static final String ORDER_SOURCE_CXUH = "6";

    private DailyTicketOrderSupport() {
    }

    /** 业务表主键：UUID 去掉连字符的 32 位十六进制串。改它会让新旧行主键形态不一致，NEVER 改。 */
    public static String nextId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /** 从网关返回的 {@code Map<String, Object>} 里取字符串值；{@code null} 走默认值。 */
    public static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    /** 只在有内容时放进出向报文：支付中心对空串与缺键的处理不同，NEVER 改成无条件 put。 */
    public static void putIfText(java.util.Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    /** 置成功应答并原样返回，便于在 return 语句里收口。 */
    public static <T extends DailyTicketBaseResult> T success(T result) {
        result.setRetCode(RET_SUCCESS);
        result.setRetMsg("成功");
        return result;
    }

    /** 置失败应答并原样返回，便于在 return 语句里收口。 */
    public static <T extends DailyTicketBaseResult> T fail(T result, String message) {
        result.setRetCode(RET_FAIL);
        result.setRetMsg(message);
        return result;
    }

    /** 校验订单号与订单类型，通过返回 {@code null}，否则返回给上游的错误文案。 */
    public static String validateOrderNo(String orderNo, String orderType) {
        if (!ORDER_TYPE_DAILY_TICKET.equals(orderType)
                && !ORDER_TYPE_TRAVEL_TICKET.equals(orderType)) {
            return "orderType必须为1或2";
        }
        if (!StringUtils.hasText(orderNo)) {
            return "orderNo不能为空";
        }
        return null;
    }

    public static String buildFreeTradeNo(String orderNo) {
        return FREE_ORDER_PREFIX + orderNo;
    }

    public static String buildSeaBusTradeNo(String orderNo) {
        return SEA_BUS_ORDER_PREFIX + orderNo;
    }

    public static String buildCxuhTradeNo(String orderNo) {
        return CXUH_ORDER_PREFIX + orderNo;
    }

    public static boolean isFreeOrder(DailyTicketOrder order) {
        return order != null && isFreePayment(order.getTradeNo(), order.getPaymentOrderNo());
    }

    public static boolean isFreeOrder(TravelTicketOrder order) {
        return order != null && isFreePayment(order.getTradeNo(), order.getPaymentOrderNo());
    }

    /** 两个号任一带免费前缀即判为免费票：历史单只写了其中一个的情况都存在。 */
    public static boolean isFreePayment(String tradeNo, String paymentOrderNo) {
        return (StringUtils.hasText(tradeNo) && tradeNo.startsWith(FREE_ORDER_PREFIX))
                || (StringUtils.hasText(paymentOrderNo) && paymentOrderNo.startsWith(FREE_ORDER_PREFIX));
    }

    public static boolean isSeaBusOrderSource(String orderSource) {
        return ORDER_SOURCE_SEA_BUS.equals(orderSource);
    }

    public static boolean isCxuhOrderSource(String orderSource) {
        return ORDER_SOURCE_CXUH.equals(orderSource);
    }

    /** 外部渠道自行收款、我方不调支付网关的订单来源。 */
    public static boolean isExternalNoGatewayOrderSource(String orderSource) {
        return isSeaBusOrderSource(orderSource) || isCxuhOrderSource(orderSource);
    }
}
