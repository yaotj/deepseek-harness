package org.qd.scg.common;

/**
 * 请求级常量：由 FormDataCacheFilter 写入、分发谓语读取的属性名。
 */
public final class RequestConstants {

    private RequestConstants() {
    }

    /** {@code bizData} 原始字符串。 */
    public static final String BIZDATA_ATTR = "route-sentinel.bizData";

    /** 从 {@code bizData} 中解析出的 {@code thirdUserId}。 */
    public static final String THIRD_USER_ID_ATTR = "route-sentinel.thirdUserId";

    /** 从 {@code bizData} 中解析出的 {@code cardId}。 */
    public static final String CARD_ID_ATTR = "route-sentinel.cardId";

    /** 从 {@code bizData} 中解析出的 {@code orderNo}（TVM 取票/出票上报）。 */
    public static final String ORDER_NO_ATTR = "route-sentinel.orderNo";

    /** 从 {@code bizData} 中解析出的 {@code randomFact}（取票二维码随机因子）。 */
    public static final String RANDOM_FACT_ATTR = "route-sentinel.randomFact";
}
