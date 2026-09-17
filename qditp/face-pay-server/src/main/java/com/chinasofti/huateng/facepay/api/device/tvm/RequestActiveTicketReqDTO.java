package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF2A-07 取票订单激活请求报文。 */
public class RequestActiveTicketReqDTO extends BaseDeviceRequest {

    /** 订单号。 */
    private String orderNo;

    /** 二维码生成日期，与 {@code randomFact} 一起构成取票凭证。 */
    private String qrcodeGenDate;

    /** 二维码随机因子。 */
    private String randomFact;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(String qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestActiveTicketReqDTO{orderNo=" + orderNo
                + ", qrcodeGenDate=" + qrcodeGenDate
                + ", randomFact=" + randomFact + '}';
    }
}
