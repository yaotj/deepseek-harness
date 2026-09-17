package com.chinasofti.huateng.model.paysign;

/**
 * 解约申请批处理内部接口响应（/internal/termination/process）。
 */
public class ProcessTerminationRespDTO {

    private String resultCode;
    private String resultMsg;

    /** 本次扫描到的待处理解约申请条数（PENDING + SCANNING）。 */
    private int scanned;

    /** 已发起支付平台解约的条数。 */
    private int terminated;

    /** 主动查询确认支付平台已解约、已收口为 SUCCESS 的条数。 */
    private int confirmed;

    /** 因存在未结清扣费订单而置 FAILED 的条数。 */
    private int rejected;

    /**
     * 因 SCANNING 收口超时而置 FAILED 的条数。
     */
    private int expired;

    /** 未能定论、状态原样保留、留待下次重试的条数。 */
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

    public int getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(int confirmed) {
        this.confirmed = confirmed;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
    }

    public int getExpired() {
        return expired;
    }

    public void setExpired(int expired) {
        this.expired = expired;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }
}
