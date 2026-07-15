package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

public class RequestSignInsDataRespDTO extends CommonResult {
    private String industryDataSign;

    public String getIndustryDataSign() {
        return industryDataSign;
    }

    public void setIndustryDataSign(String industryDataSign) {
        this.industryDataSign = industryDataSign;
    }
}
