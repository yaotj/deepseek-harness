package com.chinasofti.huateng.model.app;

/**
 * IF8A-35 查询用户账务信息应答。
 *
 * <p>两个数量都来自 {@code GATE_TXN_PAY} 的 {@code DEBIT_STATUS}，按白名单分档（2026-09-08 业务确认）：
 * {@code unpaidCount} = {@code INIT} + {@code PROCESSING}（尚未收到扣费终态），
 * {@code failureCount} = {@code FAIL} + {@code RETRY}（已判失败，含待补扣）。
 * {@code CLOSED} 与脏数据 {@code NULL} 两档都不计入——白名单口径下它们自然落空，
 * 因此 {@code unpaidCount + failureCount} 不一定等于「全部非 SUCCESS 订单数」，
 * 与解约校验 {@code countFailedOrder} / 黑名单 {@code countUnsettledOrderByCardId}
 * 的「非 SUCCESS 即未结清」口径**不是同一个**，比对两边数字前 MUST 先看清各自定义。</p>
 *
 * <p>调用方 MUST 先判断 {@code retCode} 是否为 "0000" 再使用两个数量。查询未真正执行时
 * （参数缺失、下游异常）两个数量均为 0，**NEVER** 把它当成「该用户无欠费」——
 * 这只是「没查到」。放行类判定（过闸、解约）各有自己的校验，NEVER 改用本接口的结果。</p>
 */
public class RequestUserAccInfoResult {

    /** 返回码，"0000" 表示查询成功执行。 */
    private String retCode;

    /** 返回消息。 */
    private String retMsg;

    /** 未支付订单数（DEBIT_STATUS 为 INIT / PROCESSING）。 */
    private int unpaidCount;

    /** 扣费失败订单数（DEBIT_STATUS 为 FAIL / RETRY）。 */
    private int failureCount;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public int getUnpaidCount() {
        return unpaidCount;
    }

    public void setUnpaidCount(int unpaidCount) {
        this.unpaidCount = unpaidCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    @Override
    public String toString() {
        return "RequestUserAccInfoResult{retCode='" + retCode + "', retMsg='" + retMsg
                + "', unpaidCount=" + unpaidCount + ", failureCount=" + failureCount + '}';
    }
}
