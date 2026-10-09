package com.chinasofti.huateng.model.app;

/**
 * 黑名单自动解除的批次执行结果。
 *
 * <p>六个计数互斥且相加等于 {@code scanned}，每一个都对应一条明确的处置：
 * {@code released} 已发起解除、{@code unsettled} 仍有欠费、{@code unknown} 欠费查询失败（事实不明）、
 * {@code skipped} 渠道不可路由（{@code CHANNEL_CODE='99'} 未知渠道）、{@code failed} 解除动作自身失败。
 *
 * <p><b>MUST 保持普通 POJO（无参构造 + getter/setter），NEVER 改成 record</b> ——
 * 调用侧 {@code BlacklistClient} 用 Hutool {@code JSONUtil.toBean} 填充，record 拿不到值会静默返回全 0 对象，
 * 于是调度日志永远看不到失败数（同款坑见 {@code BlacklistClient.postOutboxScan} 的注释）。
 */
public class BlacklistAutoReleaseRespDTO {

    /** 结果码，0000 成功。 */
    private String resultCode;

    /** 结果描述。 */
    private String resultMsg;

    /** 本轮扫出的候选行数。 */
    private int scanned;

    /** 已发起解除（阶段一 CAS 成 RELEASING）的行数。 */
    private int released;

    /** 仍有未结清欠费、本轮不解除的行数。 */
    private int unsettled;

    /** 欠费查询未成功执行、事实不明因而不解除的行数。 */
    private int unknown;

    /** 渠道无法路由到欠费源（未知渠道 99）因而跳过的行数。 */
    private int skipped;

    /** 解除动作本身失败的行数，需人工核对。 */
    private int failed;

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

    public int getReleased() {
        return released;
    }

    public void setReleased(int released) {
        this.released = released;
    }

    public int getUnsettled() {
        return unsettled;
    }

    public void setUnsettled(int unsettled) {
        this.unsettled = unsettled;
    }

    public int getUnknown() {
        return unknown;
    }

    public void setUnknown(int unknown) {
        this.unknown = unknown;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }
}
