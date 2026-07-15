package com.chinasofti.huateng.model.frs.vo;

public class FareMatrixVO {
    //开始站
    private String beginStatCode;
    //终点站
    private String endStatCode;
    //费率等级
    private String fareTier;

    public String getBeginStatCode() {
        return beginStatCode;
    }

    public void setBeginStatCode(String beginStatCode) {
        this.beginStatCode = beginStatCode;
    }

    public String getEndStatCode() {
        return endStatCode;
    }

    public void setEndStatCode(String endStatCode) {
        this.endStatCode = endStatCode;
    }

    public String getFareTier() {
        return fareTier;
    }

    public void setFareTier(String fareTier) {
        this.fareTier = fareTier;
    }
}
