package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class NotiTopupResultReqDTO extends BaseDeviceRequest {

    public static final String STATUS_OK = "00";

    public static final String STATUS_FAILED = "01";

    private String orderNo;

    private String topupStatus;

    public boolean needRefund() {
        return STATUS_FAILED.equals(topupStatus);
    }

    public boolean isOk() {
        return STATUS_OK.equals(topupStatus);
    }

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
        return "NotiTopupResultReqDTO{orderNo=" + orderNo
                + ", topupStatus=" + topupStatus
                + ", deviceId=" + getDeviceId() + '}';
    }
}
