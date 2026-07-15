package com.chinasofti.huateng.common.response;

/**
 * 通用响应对象。
 */
public class AlipayCommonResponse {

    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
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

    /**
     * 构建成功响应。
     *
     * @return 响应对象
     */
    public static AlipayCommonResponse success() {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode("0000");
        response.setRetMsg("成功");
        return response;
    }

    /**
     * 构建失败响应。
     *
     * @param retMsg 错误消息
     * @return 响应对象
     */
    public static AlipayCommonResponse fail(String retMsg) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode("9999");
        response.setRetMsg(retMsg);
        return response;
    }
}
