package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF2A-08 取票鉴权（按二维码三要素反查订单）请求报文。
 * 对应 {@code POST /itptvm/ci/tvm/requestTakeTicketAuth} 的 {@code bizData}。
 *
 * <p><b>没有 orderNo</b>：设备扫到二维码后只有 {@code (deviceId, qrcodeGenDate, randomFact)}，
 * 靠这三要素反查订单，命中 {@code IDX_F2F_ORDER_QRCODE}。</p>
 */
public class RequestTakeTicketAuthReqDTO extends BaseDeviceRequest {

    /** 二维码生成日期。必填。 */
    private String qrcodeGenDate;

    /** 二维码随机因子。必填。 */
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
