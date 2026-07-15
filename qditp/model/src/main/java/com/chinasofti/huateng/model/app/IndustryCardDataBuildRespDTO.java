package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 行业卡数据生成结果。
 */
public class IndustryCardDataBuildRespDTO extends CommonResult {
    private String cardData;
    private String unsignedIndustryData;

    public String getCardData() {
        return cardData;
    }

    public void setCardData(String cardData) {
        this.cardData = cardData;
    }

    public String getUnsignedIndustryData() {
        return unsignedIndustryData;
    }

    public void setUnsignedIndustryData(String unsignedIndustryData) {
        this.unsignedIndustryData = unsignedIndustryData;
    }
}
