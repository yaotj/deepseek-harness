package com.chinasofti.huateng.paysign.port;

import java.util.Map;

public interface RefundGatewayPort {

    GatewayReply requestRefund(Map<String, Object> bizData);

    GatewayReply queryRefund(Map<String, Object> bizData);

    boolean refundQueryConfigured();


    String refundNotifyUrl();
}
