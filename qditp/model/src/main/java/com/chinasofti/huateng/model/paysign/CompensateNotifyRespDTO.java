package com.chinasofti.huateng.model.paysign;

/**
 * 通知补偿批处理内部接口响应（签约 /internal/paySign/compensateNotify。
 */
public class CompensateNotifyRespDTO {

    private String resultCode;
    private String resultMsg;

    /** 本次扫描到的待补偿通知条数。 */
    private int scanned;

    /** 已提交重发的条数。 */
    private int submitted;

    /** 提交重发时即失败、状态原样保留的条数。 */
    private int skipped;

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

    public int getScanned() {
        return scanned;
    }

    public void setScanned(int scanned) {
        this.scanned = scanned;
    }

    public int getSubmitted() {
        return submitted;
    }

    public void setSubmitted(int submitted) {
        this.submitted = submitted;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    @Override
    public String toString() {
        return "CompensateNotifyRespDTO{resultCode='" + resultCode + "', resultMsg='" + resultMsg
                + "', scanned=" + scanned + ", submitted=" + submitted + ", skipped=" + skipped + '}';
    }
}
