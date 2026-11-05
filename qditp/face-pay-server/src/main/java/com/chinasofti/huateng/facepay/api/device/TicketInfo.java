package com.chinasofti.huateng.facepay.api.device;

public class TicketInfo {

    private String ticketLogicNum;

    private String transDate;

    private String transAmount;

    public Long priceInFen() {
        if (transAmount == null || transAmount.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(transAmount.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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

    @Override
    public String toString() {
        return "TicketInfo{ticketLogicNum=" + ticketLogicNum
                + ", transDate=" + transDate
                + ", transAmount=" + transAmount + '}';
    }
}
