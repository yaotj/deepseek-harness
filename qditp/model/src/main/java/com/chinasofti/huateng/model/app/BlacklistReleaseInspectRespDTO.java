package com.chinasofti.huateng.model.app;

import java.util.List;

/**
 * 黑名单「可解除性」只读盘点响应。
 *
 * <p>供 web-server 的 Quartz 任务调用 blacklist-server 内部接口后判定本轮结果。
 * 字段与 blacklist-server 侧同名类一一对应，改动 MUST 两侧同步。</p>
 *
 * <p><b>本接口 NEVER 删除任何黑名单记录。</b>当前阶段只输出盘点明细供人工核对——
 * {@code BLACKLIST} 无拉黑类型字段，「欠费结清」与「可以解除」不等价（挂失补卡类同样会结清）。
 * 等拉黑类型落库、且支付宝侧误拉黑修复后，才能在此基础上加自动删除。</p>
 */
public class BlacklistReleaseInspectRespDTO {

    /** 处理结果码，"0000" 表示本轮盘点正常执行完。 */
    private String resultCode;

    /** 处理结果描述。 */
    private String resultMsg;

    /** 本轮扫到的黑名单记录数。 */
    private int scanned;

    /** 两个欠费源都查成功且都无欠费的记录数。 */
    private int settled;

    /** 至少一个欠费源仍有欠费的记录数。 */
    private int unsettled;

    /** 至少一个欠费源查询失败、事实不明的记录数，大于 0 需人工关注。 */
    private int unknown;

    /** 盘点明细，逐条对应 {@code BLACKLIST} 的一行。 */
    private List<BlacklistReleaseCandidateDTO> details;

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

    public int getSettled() {
        return settled;
    }

    public void setSettled(int settled) {
        this.settled = settled;
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

    public List<BlacklistReleaseCandidateDTO> getDetails() {
        return details;
    }

    public void setDetails(List<BlacklistReleaseCandidateDTO> details) {
        this.details = details;
    }
}
