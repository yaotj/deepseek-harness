package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/**
 * IF2A-09 BOM 票卡退款请求 DTO。
 * BOM 向 ITP 平台发起票卡退款请求时使用的业务参数。
 */
public class RequestTicketRefundReqDTO extends BaseRequestDTO {

    /**
     * 原订单号。
     */
    private String orderNo;

    /**
     * 逻辑卡号。
     */
    private String ticketLogicNum;

    /**
     * 退款金额（单位：分）。
     */
    private String transAmount;

    /**
     * 交易类型。
     */
    private String transType;

    /**
     * 退款流水号。
     */
    private String refundNo;

    /**
     * 出票日期（格式：YYYYMMDD）。
     */
    private String ticketRefundDate;

    /**
     * 取票数量。
     */
    private String takeTicketNum;

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

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getTicketRefundDate() {
        return ticketRefundDate;
    }

    public void setTicketRefundDate(String ticketRefundDate) {
        this.ticketRefundDate = ticketRefundDate;
    }

    public String getTakeTicketNum() {
        return takeTicketNum;
    }

    public void setTakeTicketNum(String takeTicketNum) {
        this.takeTicketNum = takeTicketNum;
    }
}
