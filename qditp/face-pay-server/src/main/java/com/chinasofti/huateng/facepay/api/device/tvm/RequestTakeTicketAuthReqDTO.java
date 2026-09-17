package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF2A-08 取票鉴权（按二维码三要素反查订单）请求报文。 */
public class RequestTakeTicketAuthReqDTO extends BaseDeviceRequest {

    /** 二维码生成日期。 */
    private String qrcodeGenDate;

    /** 二维码随机因子。 */
    private String randomFact;

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
        return super.toString() + ",RequestTakeTicketAuthReqDTO{qrcodeGenDate=" + qrcodeGenDate
                + ", randomFact=" + randomFact + '}';
    }
}
