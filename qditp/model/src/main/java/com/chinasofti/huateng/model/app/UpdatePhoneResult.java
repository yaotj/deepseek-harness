package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 更换手机号响应DTO。
 */
public class UpdatePhoneResult extends CommonResult {
    private String thirdUserId;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }
}
