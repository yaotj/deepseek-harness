package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/**
 * IF2A-08 业务操作结果通知请求DTO。
 * BOM业务操作完成后，向ITP平台通知操作结果时使用的业务参数。
 */
public class NotiBusResultReqDTO extends BaseRequestDTO {

    /**
     * 订单号。
     * 需要通知业务操作结果的订单编号。
     */
    private String orderNo;

    /**
     * 操作结果通知。
     * SUCCESS：成功
     * FAILED：失败
     */
    private String optResult;

    /**
     * 操作结果描述。
     */
    private String optResultDesc;

    /**
     * 交易时间。
     * 格式：YYYYMMDDHHMMSS
     */
    private String tranDate;

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
        return "NotiBusResultReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", optResult='" + optResult + '\'' +
                ", optResultDesc='" + optResultDesc + '\'' +
                ", tranDate='" + tranDate + '\'' +
                ", providerId='" + getProviderId() + '\'' +
                ", deviceId='" + getDeviceId() + '\'' +
                '}';
    }
}