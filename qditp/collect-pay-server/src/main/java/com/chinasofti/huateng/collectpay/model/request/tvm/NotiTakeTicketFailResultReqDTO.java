package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

import java.util.List;

/**
 * IF2A-05 出票故障通知请求报文（TVM -> ITP）。
 */
public class NotiTakeTicketFailResultReqDTO extends BaseRequestDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 实际出票数量。
     */
    private String actualTakeTicketNum;

    /**
     * 故障时间（格式：YYYYMMDDHH24mmss）。
     * 错误代码为2101时填写取票二维码中的生成时间。
     */
    private String faultOccurDate;

    /**
     * 故障凭条号。
     */
    private String faultSlipSeq;

    /**
     * 错误代码。
     * 2101：取票二维码超时，解锁订单。
     */
    private String errorCode;

    /**
     * 执行错误信息。
     */
    private String errorMessage;

    /**
     * 已经写卡数据列表。
     */
    private List<NotiTakeTicketFailResultReqDTO.TicketInfo> ticketList;

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

    public List<NotiTakeTicketFailResultReqDTO.TicketInfo> getTicketList() {
        return ticketList;
    }

    public void setTicketList(List<NotiTakeTicketFailResultReqDTO.TicketInfo> ticketList) {
        this.ticketList = ticketList;
    }

    @Override
    public String toString() {
        return "NotiTakeTicketFailResultReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", actualTakeTicketNum='" + actualTakeTicketNum + '\'' +
                ", faultOccurDate='" + faultOccurDate + '\'' +
                ", faultSlipSeq='" + faultSlipSeq + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", ticketList=" + ticketList +
                '}';
    }

    /**
     * 票卡信息。
     */
    public static class TicketInfo {
        /**
         * 票卡逻辑号。
         */
        private String ticketLogicNum;

        /**
         * 交易日期（格式：YYYYMMDDHHMMSS）。
         */
        private String transDate;

        /**
         * 交易金额。
         */
        private String transAmount;

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

        @Override
        public String toString() {
            return "TicketInfo{" +
                    "ticketLogicNum='" + ticketLogicNum + '\'' +
                    ", transDate='" + transDate + '\'' +
                    ", transAmount='" + transAmount + '\'' +
                    '}';
        }
    }
}
