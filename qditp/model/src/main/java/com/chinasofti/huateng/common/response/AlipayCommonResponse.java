package com.chinasofti.huateng.common.response;


public class AlipayCommonResponse {


    private String retCode;


    private String retMsg;

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


    public static AlipayCommonResponse success() {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode("0000");
        response.setRetMsg("成功");
        return response;
    }


    public static AlipayCommonResponse fail(String retMsg) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode("9999");
        response.setRetMsg(retMsg);
        return response;
    }
}
