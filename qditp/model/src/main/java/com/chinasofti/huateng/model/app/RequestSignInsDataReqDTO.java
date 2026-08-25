package com.chinasofti.huateng.model.app;

public class RequestSignInsDataReqDTO {
    private String industryData;
    private String logicNum;

    public String getIndustryData() {
        return industryData;
    }

    public void setIndustryData(String industryData) {
        this.industryData = industryData;
    }

    public String getLogicNum() {
        return logicNum;
    }

    public void setLogicNum(String logicNum) {
        this.logicNum = logicNum;
    }

    @Override
    public String toString() {
        return "RequestSignInsDataReqDTO{industryData='" + (industryData != null ? "[length=" + industryData.length() + "]" : null)
                + "', logicNum='" + logicNum + "'}";
    }
}
