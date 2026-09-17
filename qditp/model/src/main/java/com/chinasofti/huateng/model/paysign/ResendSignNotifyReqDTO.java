package com.chinasofti.huateng.model.paysign;

/**
 * 单条签约结果通知重发请求（内部接口 /internal/paySign/resendNotify）。
 */
public class ResendSignNotifyReqDTO {

    /** 签约流水号，必填。 */
    private String requestSignSeq;

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }
}
