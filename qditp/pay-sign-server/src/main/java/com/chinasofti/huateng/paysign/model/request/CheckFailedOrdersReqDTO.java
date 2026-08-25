package com.chinasofti.huateng.paysign.model.request;

import java.time.LocalDateTime;

/**
 * 查询扣费失败订单内部接口请求。
 */
public class CheckFailedOrdersReqDTO {

    private String thirdUserId;
    private String paymentVendor;
    private LocalDateTime requestTime;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }
}
