package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class NotiBusResultReqDTO extends BaseDeviceRequest {

    private String optResult;

    private String orderNo;

    private String optResultDesc;

    private String tranDate;

    public boolean isFailed() {
        return "FAILED".equals(optResult);
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(optResult);
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getOptResult() {
        return optResult;
    }

    public void setOptResult(String optResult) {
        this.optResult = optResult;
    }

    public String getOptResultDesc() {
        return optResultDesc;
    }

    public void setOptResultDesc(String optResultDesc) {
        this.optResultDesc = optResultDesc;
    }

    public String getTranDate() {
        return tranDate;
    }

    public void setTranDate(String tranDate) {
        this.tranDate = tranDate;
    }

    @Override
    public String toString() {
        return "NotiBusResultReqDTO{orderNo=" + orderNo
                + ", optResult=" + optResult
                + ", optResultDesc=" + optResultDesc
                + ", tranDate=" + tranDate
                + ", deviceId=" + getDeviceId() + '}';
    }
}
