package com.chinasofti.huateng.model.app;


public class QueryActiveCardsReqDTO {

    private String thirdUserId;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    @Override
    public String toString() {
        return "QueryActiveCardsReqDTO{thirdUserId='" + thirdUserId + "'}";
    }
}
