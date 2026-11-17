package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;


public class RequestSignInsDataRespDTO extends AccBizBaseResponse {

    private String industryDataSign;

    public String getIndustryDataSign() {
        return industryDataSign;
    }

    public void setIndustryDataSign(String industryDataSign) {
        this.industryDataSign = industryDataSign;
    }
}
