package com.chinasofti.huateng.collectticket.model.request;

import java.util.List;

/**
 * IF2A-03 取票订单通知请求报文。
 */
public class TicketCollectNotifyReqDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 用户ID。
     */
    private String userId;

    /**
     * 取票状态：100-取票成功，1-99-取票失败。
     */
    private Integer collectStatus;

    /**
     * 取票设备编号。
     */
    private String deviceId;

    /**
     * 实际出票数量。
     */
    private Integer actualTakeTicketNum;

    /**
     * 故障凭证号。
     */
    private String faultSlipSeq;

    /**
     * 错误代码。
     */
    private String errorCode;

    /**
     * 错误详细信息。
     */
    private String errorMessage;

    /**
     * 取票明细列表。
     */
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

    /**
     * 取票明细。
     */
    public static class TicketDetail {
        /**
         * 实际出票的第几张。
         */
        private Integer inOrderNo;

        /**
         * 取票时间。
         */
        private String takeTicketDate;

        /**
         * 票卡逻辑号。
         */
        private String ticketLogicNum;

        /**
         * 交易时间。
         */
        private String transDate;

        /**
         * 交易金额。
         */
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
