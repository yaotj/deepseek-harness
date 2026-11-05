package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;
import com.chinasofti.huateng.facepay.api.device.TicketInfo;

import java.util.List;

public class NotiTakeTicketFailResultReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String actualTakeTicketNum;

    private String faultOccurDate;

    private String faultSlipSeq;

    private String errorCode;

    private String errorMessage;

    private List<TicketInfo> ticketList;

    public Integer actualNum() {
        if (actualTakeTicketNum == null || actualTakeTicketNum.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(actualTakeTicketNum.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(String actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
    }

    public String getFaultOccurDate() {
        return faultOccurDate;
    }

    public void setFaultOccurDate(String faultOccurDate) {
        this.faultOccurDate = faultOccurDate;
    }

    public String getFaultSlipSeq() {
        return faultSlipSeq;
    }

    public void setFaultSlipSeq(String faultSlipSeq) {
        this.faultSlipSeq = faultSlipSeq;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<TicketInfo> getTicketList() {
        return ticketList;
    }

    public void setTicketList(List<TicketInfo> ticketList) {
        this.ticketList = ticketList;
    }

    @Override
    public String toString() {
        return super.toString() + ",NotiTakeTicketFailResultReqDTO{orderNo=" + orderNo
                + ", actualTakeTicketNum=" + actualTakeTicketNum
                + ", faultSlipSeq=" + faultSlipSeq
                + ", errorCode=" + errorCode + '}';
    }
}
