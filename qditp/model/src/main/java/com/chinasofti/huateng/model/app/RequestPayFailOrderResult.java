package com.chinasofti.huateng.model.app;

/**
 * 用户主动发起免密失败订单重试扣费应答（APP 接口 requestPayFailOrder）。
 *
 * <p>与 {@link RequestUserAccInfoResult} 同款约定：不继承 {@code CommonResult}，自行声明
 * {@code retCode} / {@code retMsg}（统一解析响应的测试框架 NEVER 假定所有响应是同一基类）。
 * 重试笔数只进 {@code retMsg} 文案，不另开字段——完全对齐接口规范表163（仅 retCode / retMsg 两列）。
 */
public class RequestPayFailOrderResult {

    /** 返回码，0000 表示受理成功。 */
    private String retCode;

    /** 返回消息。 */
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

    @Override
    public String toString() {
        return "RequestPayFailOrderResult{retCode='" + retCode + "', retMsg='" + retMsg + "'}";
    }
}
