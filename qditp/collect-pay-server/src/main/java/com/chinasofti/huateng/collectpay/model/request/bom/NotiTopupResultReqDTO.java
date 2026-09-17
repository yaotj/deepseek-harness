package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/** 充值结果通知请求DTO。 */
public class NotiTopupResultReqDTO extends BaseRequestDTO {

    /** 订单号。 */
    private String orderNo;

    /** 充值状态。 */
    private String topupStatus;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTopupStatus() {
        return topupStatus;
    }

    public void setTopupStatus(String topupStatus) {
        this.topupStatus = topupStatus;
    }

    @Override
    public String toString() {
        return "NotiTopupResultReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", topupStatus='" + topupStatus + '\'' +
                ", providerId='" + getProviderId() + '\'' +
                ", deviceId='" + getDeviceId() + '\'' +
                '}';
    }
}
