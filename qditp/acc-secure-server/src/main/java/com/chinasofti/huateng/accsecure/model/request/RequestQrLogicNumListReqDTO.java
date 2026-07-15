package com.chinasofti.huateng.accsecure.model.request;

/**
 * IF7B-01 请求逻辑卡号请求报文。
 */
public class RequestQrLogicNumListReqDTO {
    /**
     * 请求数量，文档默认 10 万。
     */
    private String requestNum;

    public String getRequestNum() {
        return requestNum;
    }

    public void setRequestNum(String requestNum) {
        this.requestNum = requestNum;
    }
}
