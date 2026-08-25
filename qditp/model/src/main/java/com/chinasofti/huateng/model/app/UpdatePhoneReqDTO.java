package com.chinasofti.huateng.model.app;

/**
 * 更换手机号请求DTO。
 */
public class UpdatePhoneReqDTO {
    private String thirdUserId;
    private String newMsisdn;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getNewMsisdn() {
        return newMsisdn;
    }

    public void setNewMsisdn(String newMsisdn) {
        this.newMsisdn = newMsisdn;
    }
}
