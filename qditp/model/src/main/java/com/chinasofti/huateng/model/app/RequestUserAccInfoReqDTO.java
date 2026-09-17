package com.chinasofti.huateng.model.app;

/**
 * IF8A-35 查询用户账务信息请求。
 */
public class RequestUserAccInfoReqDTO {

    /** 第三方用户 id。 */
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
