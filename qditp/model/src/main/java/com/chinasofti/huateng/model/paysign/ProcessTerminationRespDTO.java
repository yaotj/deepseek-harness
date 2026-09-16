package com.chinasofti.huateng.model.paysign;

/**
 * 解约申请批处理内部接口响应（/internal/termination/process）。
 *
 * <p>scanned 为本次取到的待处理条数（PENDING + SCANNING）；调用方可反复调用直到 scanned 为 0。
 * skipped 表示本次未能定论、状态原样保留的条数，下次扫表会重试。</p>
 *
 * <p>放在 model 模块而不是 pay-sign-server：本接口经 {@code PaySignClient} 被 web-server 的
 * Quartz 任务调用，rpc 模块必须能看到这个类型。</p>
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
     * 非 0 就该有人看：说明我方口径与支付平台侧可能已不一致，需人工核对协议是否真的解约。
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
