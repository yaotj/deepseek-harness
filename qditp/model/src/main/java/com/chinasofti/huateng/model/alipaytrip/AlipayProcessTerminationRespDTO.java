package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行销卡批处理响应。
 *
 * <p>调用方 MUST 先判断 {@code resultCode} 为 "0000" 再看计数，否则会把「查询未执行」误读成「无待处理记录」。</p>
 *
 * <p>{@code scanned} 为 0 表示本轮没有待处理记录，调用方可据此结束排空循环。</p>
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
     * 当前唯一的跳过原因是登记记录缺 THIRD_USER_ID（历史脏数据，无法做用户维度校验）。
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
