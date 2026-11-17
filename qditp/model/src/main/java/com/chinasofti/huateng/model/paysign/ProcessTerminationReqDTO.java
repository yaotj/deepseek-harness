package com.chinasofti.huateng.model.paysign;


public class ProcessTerminationReqDTO {


    private String referenceTime;


    private Integer delayDays;

    public String getReferenceTime() {
        return referenceTime;
    }

    public void setReferenceTime(String referenceTime) {
        this.referenceTime = referenceTime;
    }

    public Integer getDelayDays() {
        return delayDays;
    }

    public void setDelayDays(Integer delayDays) {
        this.delayDays = delayDays;
    }

    @Override
    public String toString() {
        return "ProcessTerminationReqDTO{referenceTime='" + referenceTime + "', delayDays=" + delayDays + '}';
    }
}
