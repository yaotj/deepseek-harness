package com.chinasofti.huateng.collectticket.model.request;

import java.util.List;


public class TicketCollectNotifyReqDTO {

    private String orderNo;


    private String userId;


    private Integer collectStatus;


    private String deviceId;


    private Integer actualTakeTicketNum;


    private String faultSlipSeq;


    private String errorCode;


    private String errorMessage;


    private List<TicketDetail> ticketDetails;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getCollectStatus() {
        return collectStatus;
    }

    public void setCollectStatus(Integer collectStatus) {
        this.collectStatus = collectStatus;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Integer getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(Integer actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
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

    public List<TicketDetail> getTicketDetails() {
        return ticketDetails;
    }

    public void setTicketDetails(List<TicketDetail> ticketDetails) {
        this.ticketDetails = ticketDetails;
    }

    @Override
    public String toString() {
        return "TicketCollectNotifyReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", collectStatus=" + collectStatus +
                ", deviceId='" + deviceId + '\'' +
                ", actualTakeTicketNum=" + actualTakeTicketNum +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }


    public static class TicketDetail {

        private Integer inOrderNo;


        private String takeTicketDate;


        private String ticketLogicNum;


        private String transDate;


        private String transAmount;

        public Integer getInOrderNo() {
            return inOrderNo;
        }

        public void setInOrderNo(Integer inOrderNo) {
            this.inOrderNo = inOrderNo;
        }

        public String getTakeTicketDate() {
            return takeTicketDate;
        }

        public void setTakeTicketDate(String takeTicketDate) {
            this.takeTicketDate = takeTicketDate;
        }

        public String getTicketLogicNum() {
            return ticketLogicNum;
        }

        public void setTicketLogicNum(String ticketLogicNum) {
            this.ticketLogicNum = ticketLogicNum;
        }

        public String getTransDate() {
            return transDate;
        }

        public void setTransDate(String transDate) {
            this.transDate = transDate;
        }

        public String getTransAmount() {
            return transAmount;
        }

        public void setTransAmount(String transAmount) {
            this.transAmount = transAmount;
        }
    }
}
