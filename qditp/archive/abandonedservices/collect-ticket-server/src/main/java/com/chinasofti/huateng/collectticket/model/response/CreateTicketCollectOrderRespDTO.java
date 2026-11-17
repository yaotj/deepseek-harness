package com.chinasofti.huateng.collectticket.model.response;


public class CreateTicketCollectOrderRespDTO {

    private String retCode;


    private String retMsg;


    private String orderNo;


    private Integer orderStatus;


    private Integer ticketPrice;


    private String qrcodeGenDate;


    private String randomFact;


    private Integer singleTicketType;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
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

    public Integer getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(Integer singleTicketType) {
        this.singleTicketType = singleTicketType;
    }

    @Override
    public String toString() {
        return "CreateTicketCollectOrderRespDTO{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", orderStatus=" + orderStatus +
                ", ticketPrice=" + ticketPrice +
                ", qrcodeGenDate='" + qrcodeGenDate + '\'' +
                ", randomFact='" + randomFact + '\'' +
                ", singleTicketType=" + singleTicketType +
                '}';
    }
}
