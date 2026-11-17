package com.chinasofti.huateng.model.alipaytrip;


public class AlipayProcessTerminationReqDTO {

    private String referenceTime;

    public String getReferenceTime() {
        return referenceTime;
    }

    public void setReferenceTime(String referenceTime) {
        this.referenceTime = referenceTime;
    }

    @Override
    public String toString() {
        return "AlipayProcessTerminationReqDTO{referenceTime='" + referenceTime + "'}";
    }
}
