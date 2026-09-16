package com.chinasofti.huateng.model.app;

/**
 * IF8A-75 直接解绑支付方式应答（接口规范 §3.59）。
 *
 * <p>返回 {@code 0000} 的含义是「已向支付渠道发起解绑」，<b>不等于「已解绑完成」</b>。
 * 支付渠道解约本身是异步的，且回调地址由支付中心侧的商户配置决定、不由我方指定，
 * 最终收口以 pay-sign 的 {@code processTermination} 主动查询 §2.4 {@code queryResult}
 * （{@code status=UNSIGNED} 即已解约）为准。APP <b>NEVER</b> 把本接口的 {@code 0000}
 * 当成解绑成功直接刷新界面为「未绑定」。</p>
 *
 * <p>幂等：解约申请已处于 {@code SCANNING} / {@code SUCCESS} / {@code FAILED} 时同样返回 {@code 0000}。</p>
 */
public class UnbindAgreementResult {

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
        return "UnbindAgreementResult{retCode='" + retCode + "', retMsg='" + retMsg + "'}";
    }
}
