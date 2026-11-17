package com.chinasofti.huateng.collectticket.entity;

import java.time.LocalDateTime;


public class TicketCollectLogDetail {

    private String orderNo;


    private Integer actualTakeTicketNum;


    private Integer inOrderNo;


    private LocalDateTime takeTickeDate;


    private String ticketLogicNum;


    private LocalDateTime transDate;


    private String transAmount;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(Integer actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
    }

    public Integer getInOrderNo() {
        return inOrderNo;
    }

    public void setInOrderNo(Integer inOrderNo) {
        this.inOrderNo = inOrderNo;
    }

    public LocalDateTime getTakeTickeDate() {
        return takeTickeDate;
    }

    public void setTakeTickeDate(LocalDateTime takeTickeDate) {
        this.takeTickeDate = takeTickeDate;
    }

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public LocalDateTime getTransDate() {
        return transDate;
    }

    public void setTransDate(LocalDateTime transDate) {
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
        return "TicketCollectLogDetail{" +
                "orderNo='" + orderNo + '\'' +
                ", actualTakeTicketNum=" + actualTakeTicketNum +
                ", inOrderNo=" + inOrderNo +
                ", takeTickeDate=" + takeTickeDate +
                ", ticketLogicNum='" + ticketLogicNum + '\'' +
                ", transDate=" + transDate +
                ", transAmount='" + transAmount + '\'' +
                '}';
    }
}
