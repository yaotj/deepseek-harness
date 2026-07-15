package com.chinasofti.huateng.accsecure.model.request;

/**
 * IF7B-08 请求发售HCE单程票请求报文。
 */
public class RequestHecCardDateReqDTO {
    /**
     * 票种，文档示例：00-计时票，02-后付费。
     */
    private String otp;

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
