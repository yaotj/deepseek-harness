package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;

/**
 * IF7B-01 请求逻辑卡号响应报文。
 */
public class RequestQrLogicNumListRespDTO extends AccBizBaseResponse {
    private String fileName;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
