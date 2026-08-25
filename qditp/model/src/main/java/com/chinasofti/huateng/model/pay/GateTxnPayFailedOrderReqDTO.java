package com.chinasofti.huateng.model.pay;

import java.time.LocalDateTime;

/**
 * 查询用户指定支付渠道在指定时间之后是否存在扣费失败订单。
 */
public class GateTxnPayFailedOrderReqDTO {

    /** 第三方用户 ID。 */
    private String thirdUserId;

    /** 支付渠道编码。 */
    private String paymentVendor;

    /** 解约申请时间，只查询该时间之后产生的扣费失败订单。 */
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
