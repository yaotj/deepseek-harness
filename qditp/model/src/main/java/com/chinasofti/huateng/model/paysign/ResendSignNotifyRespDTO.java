package com.chinasofti.huateng.model.paysign;

/**
 * 单条签约结果通知重发响应（内部接口 /internal/paySign/resendNotify）。
 */
public class ResendSignNotifyRespDTO {

    private String resultCode;
    private String resultMsg;

    /** 本次重发的签约流水号。 */
    private String requestSignSeq;

    /** 通知是否投递成功（HTTP 2xx 且 retCode 命中 app.notify.success-ret-codes）。 */
    private boolean notified;

    /** 通知结果描述，与落库的 NOTIFY_RESULT 一致。 */
    private String notifyResult;

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public String getNotifyResult() {
        return notifyResult;
    }

    public void setNotifyResult(String notifyResult) {
        this.notifyResult = notifyResult;
    }
}
