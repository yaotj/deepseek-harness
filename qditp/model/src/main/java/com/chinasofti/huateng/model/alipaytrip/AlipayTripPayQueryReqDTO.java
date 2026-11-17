package com.chinasofti.huateng.model.alipaytrip;


public class AlipayTripPayQueryReqDTO {


    private String orderNo;


    private String cardIssueCode;


    private String cardNum;


    private String channelAgreementNo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCardIssueCode() {
        return cardIssueCode;
    }

    public void setCardIssueCode(String cardIssueCode) {
        this.cardIssueCode = cardIssueCode;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getChannelAgreementNo() {
        return channelAgreementNo;
    }

    public void setChannelAgreementNo(String channelAgreementNo) {
        this.channelAgreementNo = channelAgreementNo;
    }
}
