package com.chinasofti.huateng.model.app;


public class RequestPayFailOrderReqDTO {

    private String thirdUserId;

    private String cardNums;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardNums() {
        return cardNums;
    }

    public void setCardNums(String cardNums) {
        this.cardNums = cardNums;
    }

    @Override
    public String toString() {
        return "RequestPayFailOrderReqDTO{thirdUserId='" + thirdUserId + "', cardNums='" + cardNums + "'}";
    }
}
