package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;


public class RequestSignInfoResult extends CommonResult {


    private String requestStartSdkInfo;

    public String getRequestStartSdkInfo() {
        return requestStartSdkInfo;
    }

    public void setRequestStartSdkInfo(String requestStartSdkInfo) {
        this.requestStartSdkInfo = requestStartSdkInfo;
    }
}
