package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

public class RequestContractResultResult extends CommonResult {
    private String status;
    /** 钱包用户/账户标识；传统签约渠道通常为空。 */
    private String payUserId;
    private String payAccountId;
    private String payAgreementNo;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayAccountId() {
        return payAccountId;
    }

    public String getPayUserId() {
        return payUserId;
    }

    public void setPayUserId(String payUserId) {
        this.payUserId = payUserId;
    }

    public void setPayAccountId(String payAccountId) {
        this.payAccountId = payAccountId;
    }

    public String getPayAgreementNo() {
        return payAgreementNo;
    }

    public void setPayAgreementNo(String payAgreementNo) {
        this.payAgreementNo = payAgreementNo;
    }
}
