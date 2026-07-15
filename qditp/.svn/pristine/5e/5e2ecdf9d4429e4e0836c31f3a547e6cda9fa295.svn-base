package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

import java.util.List;

/**
 * IF2A-04 出票结果通知请求报文（TVM -> ITP）。
 */
public class NotiTakeTicketResultReqDTO extends BaseRequestDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 实际出票数量。
     */
    private String actualTakeTicketNum;

    /**
     * 出票时间（格式：YYYYMMDDHHMMSS）。
     */
    private String takeTickeDate;

    /**
     * 已经写卡卡数据列表。
     */
    private List<TicketInfo> ticketList;

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

    public List<TicketInfo> getTicketList() {
        return ticketList;
    }

    public void setTicketList(List<TicketInfo> ticketList) {
        this.ticketList = ticketList;
    }

    @Override
    public String toString() {
        return "NotiTakeTicketResultReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", actualTakeTicketNum='" + actualTakeTicketNum + '\'' +
                ", takeTickeDate='" + takeTickeDate + '\'' +
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
