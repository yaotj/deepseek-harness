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
}
