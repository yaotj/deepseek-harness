package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-查询乘车记录详情响应 VO。
 */
public class AlipayTripFindTravelDetailRespVO {

    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 原信息。
     */
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
