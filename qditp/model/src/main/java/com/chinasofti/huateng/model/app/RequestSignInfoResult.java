package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * @author zzm
 * @date 2026/5/26 7:37
 */
public class RequestSignInfoResult extends CommonResult {

    /**
     * 调用SDK所需的请求参数
     */
    private String requestStartSdkInfo;

    public String getRequestStartSdkInfo() {
        return requestStartSdkInfo;
    }

    public void setRequestStartSdkInfo(String requestStartSdkInfo) {
        this.requestStartSdkInfo = requestStartSdkInfo;
    }
}
