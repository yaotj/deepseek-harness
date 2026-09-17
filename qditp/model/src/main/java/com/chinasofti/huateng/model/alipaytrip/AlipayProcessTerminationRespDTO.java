package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行销卡批处理响应。
 */
public class AlipayProcessTerminationRespDTO {

    private String resultCode;
    private String resultMsg;

    /** 本轮扫到的 PENDING 登记记录数。 */
    private int scanned;
    /** 销卡执行完成（登记表置 COMPLETED）的条数。 */
    private int terminated;
    /** 执行失败（登记表置 FAIL）的条数，含签约信息不存在。 */
    private int failed;
    /**
     * 本轮跳过、状态未变的条数。
     */
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

    public int getTerminated() {
        return terminated;
    }

    public void setTerminated(int terminated) {
        this.terminated = terminated;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }
}
