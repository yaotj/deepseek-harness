package com.chinasofti.huateng.model.app;


public class RequestUserAccInfoReqDTO {

    private String thirdUserId;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    @Override
    public String toString() {
        return "RequestUserAccInfoReqDTO{thirdUserId='" + thirdUserId + "'}";
    }
}
