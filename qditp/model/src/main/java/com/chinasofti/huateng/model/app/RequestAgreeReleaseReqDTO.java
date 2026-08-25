package com.chinasofti.huateng.model.app;

/**
 * IF8A-36 请求移除签约信息请求参数。
 *
 * <p>与解约不同，移除签约时 ITP 不请求支付系统，仅将签约记录状态改为解约成功。
 * 适用于用户协议在第三方已失效或钱包解绑等场景。</p>
 */
public class RequestAgreeReleaseReqDTO {
    /** 协议编码，签约时生成的唯一协议编号。 */
    private String agreementCode;

    public String getAgreementCode() {
        return agreementCode;
    }

    public void setAgreementCode(String agreementCode) {
        this.agreementCode = agreementCode;
    }

    @Override
    public String toString() {
        return "RequestAgreeReleaseReqDTO{agreementCode='" + agreementCode + "'}";
    }
}
