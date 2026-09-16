package com.chinasofti.huateng.model.app;

/**
 * IF8A-42 用户销户应答（接口规范 §3.46）。
 *
 * <p>返回码取 {@code AccountErrorCodeEnum}：{@code 0000} 成功、{@code 8001} 无效参数、
 * {@code 8023} 存在未结清订单、{@code 8024} 账务信息查询失败、{@code 9001} 系统内部错误。</p>
 *
 * <p>幂等口径：<b>已注销用户重复调用返回 {@code 0000}</b>，而不是 {@code 8004}。
 * APP 侧对失败会重试，若已注销再报错会让流程永久卡住。因此
 * <b>NEVER</b> 把「查不到有效开户记录」当成错误。</p>
 *
 * <p>与 {@code CommonResult} 不继承，对齐同链路 {@code RequestRemovePayChannelResult}
 * 等既有 IF8A 应答类的写法。</p>
 */
public class UserCancelResult {

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

    @Override
    public String toString() {
        return "UserCancelResult{retCode='" + retCode + "', retMsg='" + retMsg + "'}";
    }
}
