package com.chinasofti.huateng.online.model.tvm;

import com.chinasofti.huateng.online.model.BaseRespDTO;

import java.util.List;

public final class TvmDtos {
    private TvmDtos() {
    }

    public static class RequestGenSjtOrderReqDTO {
        private String entryStationCode;
        private String exitStationCode;
        private String ticketPrice;
        private String singelTicketNum;
        private String singleTicketType;

        public String getEntryStationCode() {
            return entryStationCode;
        }

        public void setEntryStationCode(String entryStationCode) {
            this.entryStationCode = entryStationCode;
        }

        public String getExitStationCode() {
            return exitStationCode;
        }

        public void setExitStationCode(String exitStationCode) {
            this.exitStationCode = exitStationCode;
        }

        public String getTicketPrice() {
            return ticketPrice;
        }

        public void setTicketPrice(String ticketPrice) {
            this.ticketPrice = ticketPrice;
        }

        public String getSingelTicketNum() {
            return singelTicketNum;
        }

        public void setSingelTicketNum(String singelTicketNum) {
            this.singelTicketNum = singelTicketNum;
        }

        public String getSingleTicketType() {
            return singleTicketType;
        }

        public void setSingleTicketType(String singleTicketType) {
            this.singleTicketType = singleTicketType;
        }
    }

    public static class RequestGenSjtOrderRespDTO extends BaseRespDTO {
        private String orderNo;
        private String payUrl;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getPayUrl() {
            return payUrl;
        }

        public void setPayUrl(String payUrl) {
            this.payUrl = payUrl;
        }
    }

    public static class RequestPayResultReqDTO {
        private String orderNo;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }
    }

    public static class RequestPayResultRespDTO extends BaseRespDTO {
        private String paymentChannelCode;
        private String paymentResult;
        private String paymentResultDesc;

        public String getPaymentChannelCode() {
            return paymentChannelCode;
        }

        public void setPaymentChannelCode(String paymentChannelCode) {
            this.paymentChannelCode = paymentChannelCode;
        }

        public String getPaymentResult() {
            return paymentResult;
        }

        public void setPaymentResult(String paymentResult) {
            this.paymentResult = paymentResult;
        }

        public String getPaymentResultDesc() {
            return paymentResultDesc;
        }

        public void setPaymentResultDesc(String paymentResultDesc) {
            this.paymentResultDesc = paymentResultDesc;
        }
    }

    public static class TicketItemDTO {
        private String ticketLogicNum;
        private String ticketPhysicsNum;
        private String transDate;
        private String transAmount;

        public String getTicketLogicNum() {
            return ticketLogicNum;
        }

        public void setTicketLogicNum(String ticketLogicNum) {
            this.ticketLogicNum = ticketLogicNum;
        }

        public String getTicketPhysicsNum() {
            return ticketPhysicsNum;
        }

        public void setTicketPhysicsNum(String ticketPhysicsNum) {
            this.ticketPhysicsNum = ticketPhysicsNum;
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

    public static class NotiTakeTicketResultReqDTO {
        private String orderNo;
        private String actualTakeTicketNum;
        private String takeTickeDate;
        private List<TicketItemDTO> ticketList;

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

        public String getTakeTickeDate() {
            return takeTickeDate;
        }

        public void setTakeTickeDate(String takeTickeDate) {
            this.takeTickeDate = takeTickeDate;
        }

        public List<TicketItemDTO> getTicketList() {
            return ticketList;
        }

        public void setTicketList(List<TicketItemDTO> ticketList) {
            this.ticketList = ticketList;
        }
    }

    public static class NotiTakeTicketFailResultReqDTO {
        private String orderNo;
        private String actualTakeTicketNum;
        private String faultOccurDate;
        private String faultSlipSeq;
        private String errorCode;
        private String errorMessage;
        private List<TicketItemDTO> ticketList;

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

        public List<TicketItemDTO> getTicketList() {
            return ticketList;
        }

        public void setTicketList(List<TicketItemDTO> ticketList) {
            this.ticketList = ticketList;
        }
    }

    public static class TopupCardResultNotiReqDTO {
        private String orderNo;
        private String ticketLogicNum;
        private String ticketPhysicsNum;
        private String transDate;
        private String transAmount;
        private String afterAmount;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getTicketLogicNum() {
            return ticketLogicNum;
        }

        public void setTicketLogicNum(String ticketLogicNum) {
            this.ticketLogicNum = ticketLogicNum;
        }

        public String getTicketPhysicsNum() {
            return ticketPhysicsNum;
        }

        public void setTicketPhysicsNum(String ticketPhysicsNum) {
            this.ticketPhysicsNum = ticketPhysicsNum;
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

        public String getAfterAmount() {
            return afterAmount;
        }

        public void setAfterAmount(String afterAmount) {
            this.afterAmount = afterAmount;
        }
    }

    public static class TopupCardFailNotiReqDTO {
        private String orderNo;
        private String ticketLogicNum;
        private String ticketPhysicsNum;
        private String topupStatus;
        private String faultOccurDate;
        private String faultSlipSeq;
        private String errorCode;
        private String errorMessage;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getTicketLogicNum() {
            return ticketLogicNum;
        }

        public void setTicketLogicNum(String ticketLogicNum) {
            this.ticketLogicNum = ticketLogicNum;
        }

        public String getTicketPhysicsNum() {
            return ticketPhysicsNum;
        }

        public void setTicketPhysicsNum(String ticketPhysicsNum) {
            this.ticketPhysicsNum = ticketPhysicsNum;
        }

        public String getTopupStatus() {
            return topupStatus;
        }

        public void setTopupStatus(String topupStatus) {
            this.topupStatus = topupStatus;
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
    }

    public static class RequestTakeTicketAuthReqDTO {
        private String deviceId;
        private String qrcodeGenDate;
        private String randomFact;

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
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
    }

    public static class RequestTakeTicketAuthRespDTO extends BaseRespDTO {
        private String orderNo;
        private String deviceId;
        private String entryStationCode;
        private String exitStationCode;
        private String ticketPrice;
        private String singelTicketNum;
        private String singleTicketType;
        private String paymentChannelCode;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getEntryStationCode() {
            return entryStationCode;
        }

        public void setEntryStationCode(String entryStationCode) {
            this.entryStationCode = entryStationCode;
        }

        public String getExitStationCode() {
            return exitStationCode;
        }

        public void setExitStationCode(String exitStationCode) {
            this.exitStationCode = exitStationCode;
        }

        public String getTicketPrice() {
            return ticketPrice;
        }

        public void setTicketPrice(String ticketPrice) {
            this.ticketPrice = ticketPrice;
        }

        public String getSingelTicketNum() {
            return singelTicketNum;
        }

        public void setSingelTicketNum(String singelTicketNum) {
            this.singelTicketNum = singelTicketNum;
        }

        public String getSingleTicketType() {
            return singleTicketType;
        }

        public void setSingleTicketType(String singleTicketType) {
            this.singleTicketType = singleTicketType;
        }

        public String getPaymentChannelCode() {
            return paymentChannelCode;
        }

        public void setPaymentChannelCode(String paymentChannelCode) {
            this.paymentChannelCode = paymentChannelCode;
        }
    }

    public static class RequestTopupReqDTO {
        private String ticketLogicNum;
        private String ticketPhysicsNum;
        private String beforeAmount;
        private String transAmount;

        public String getTicketLogicNum() {
            return ticketLogicNum;
        }

        public void setTicketLogicNum(String ticketLogicNum) {
            this.ticketLogicNum = ticketLogicNum;
        }

        public String getTicketPhysicsNum() {
            return ticketPhysicsNum;
        }

        public void setTicketPhysicsNum(String ticketPhysicsNum) {
            this.ticketPhysicsNum = ticketPhysicsNum;
        }

        public String getBeforeAmount() {
            return beforeAmount;
        }

        public void setBeforeAmount(String beforeAmount) {
            this.beforeAmount = beforeAmount;
        }

        public String getTransAmount() {
            return transAmount;
        }

        public void setTransAmount(String transAmount) {
            this.transAmount = transAmount;
        }
    }

    public static class RequestTopupRespDTO extends BaseRespDTO {
        private String orderNo;
        private String payUrl;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getPayUrl() {
            return payUrl;
        }

        public void setPayUrl(String payUrl) {
            this.payUrl = payUrl;
        }
    }

    public static class RequestPaymentReqDTO {
        private String orderNo;
        private String paymentCode;
        private String paymentVendor;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getPaymentCode() {
            return paymentCode;
        }

        public void setPaymentCode(String paymentCode) {
            this.paymentCode = paymentCode;
        }

        public String getPaymentVendor() {
            return paymentVendor;
        }

        public void setPaymentVendor(String paymentVendor) {
            this.paymentVendor = paymentVendor;
        }
    }

    public static class RequestPaymentRespDTO extends BaseRespDTO {
        private String paymentResult;
        private String paymentResultDesc;

        public String getPaymentResult() {
            return paymentResult;
        }

        public void setPaymentResult(String paymentResult) {
            this.paymentResult = paymentResult;
        }

        public String getPaymentResultDesc() {
            return paymentResultDesc;
        }

        public void setPaymentResultDesc(String paymentResultDesc) {
            this.paymentResultDesc = paymentResultDesc;
        }
    }
}
