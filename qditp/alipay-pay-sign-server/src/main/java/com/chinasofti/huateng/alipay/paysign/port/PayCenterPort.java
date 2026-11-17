package com.chinasofti.huateng.alipay.paysign.port;

import java.util.Map;


public interface PayCenterPort {

    PayCenterReply requestPay(Map<String, Object> bizData);

    PayCenterReply payQuery(Map<String, Object> bizData);

    PayCenterReply requestRefund(Map<String, Object> bizData);


    PayCenterReply refundQuery(Map<String, Object> bizData);
}
