package com.chinasofti.huateng.model.app;


public class QueryUserItineraryReqDTO {
    private String cardNum;


    private String cardType;


    private String phone;


    private String status;


    private String level;

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    @Override
    public String toString() {
        return "QueryUserItineraryReqDTO{cardNum='" + cardNum + "', cardType='" + cardType
                + "', phone='" + phone + "', status='" + status + "', level='" + level + "'}";
    }
}
