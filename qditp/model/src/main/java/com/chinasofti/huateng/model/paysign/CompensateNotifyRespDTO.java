package com.chinasofti.huateng.model.paysign;


public class CompensateNotifyRespDTO {

    private String resultCode;
    private String resultMsg;

    private int scanned;

    private int submitted;

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
