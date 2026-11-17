package com.chinasofti.huateng.dailyticket.service.support;

import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.springframework.util.StringUtils;


public final class DailyTicketOrderSupport {
    public static final String RET_SUCCESS = "0000";
    public static final String RET_FAIL = "9999";
    public static final String ORDER_TYPE_DAILY_TICKET = "1";
    public static final String ORDER_TYPE_TRAVEL_TICKET = "2";

    public static final String FREE_ORDER_PREFIX = "FREE-";
    public static final String SEA_BUS_ORDER_PREFIX = "SEA_BUS-";
    public static final String CXUH_ORDER_PREFIX = "CXUH-";

    public static final String ORDER_SOURCE_SEA_BUS = "4";
    public static final String ORDER_SOURCE_CXUH = "6";

    private DailyTicketOrderSupport() {
    }

    public static String nextId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    public static void putIfText(java.util.Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    public static <T extends DailyTicketBaseResult> T success(T result) {
        result.setRetCode(RET_SUCCESS);
        result.setRetMsg("成功");
        return result;
    }

    public static <T extends DailyTicketBaseResult> T fail(T result, String message) {
        result.setRetCode(RET_FAIL);
        result.setRetMsg(message);
        return result;
    }

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

    public static boolean isExternalNoGatewayOrderSource(String orderSource) {
        return isSeaBusOrderSource(orderSource) || isCxuhOrderSource(orderSource);
    }
}
