package com.chinasofti.huateng.paysign.port;

import java.util.Map;

/** 支付域看**支付中心退款方向**的窄接口（防腐层，2026-09-16，ADR-D113 续）。 */
public interface RefundGatewayPort {

    /** 支付 API 3.1 请求退款。bizData 由调用点传入（见接口注释）。 */
    GatewayReply requestRefund(Map<String, Object> bizData);

    /** 支付 API 3.2 退款查询。bizData 由调用点传入（见接口注释）。 */
    GatewayReply queryRefund(Map<String, Object> bizData);

    /** 退款查询地址是否已配置。 */
    boolean refundQueryConfigured();
}
