package com.chinasofti.huateng.paysign.port;

import java.time.LocalDateTime;


public interface UnsettledOrderPort {


    UnsettledOrderAnswer hasUnsettledOrder(String thirdUserId, String paymentVendor, LocalDateTime requestTime);
}
