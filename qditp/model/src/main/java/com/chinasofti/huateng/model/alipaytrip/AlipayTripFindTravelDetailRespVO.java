package com.chinasofti.huateng.model.alipaytrip;


public class AlipayTripFindTravelDetailRespVO {


    private String retCode;


    private String retMsg;


    private AlipayTripFindTravelDetailRespDTO data;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public AlipayTripFindTravelDetailRespDTO getData() {
        return data;
    }

    public void setData(AlipayTripFindTravelDetailRespDTO data) {
        this.data = data;
    }
}
