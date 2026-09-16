package com.chinasofti.huateng.model.paysign;

/**
 * 通知补偿批处理内部接口响应（签约 /internal/paySign/compensateNotify
 * 与解约 /internal/termination/compensateNotify 共用同一结构）。
 *
 * <p>{@code scanned} 是本次扫到的待补偿条数，{@code submitted} 是已提交重发的条数。
 * <b>重发是异步的，submitted 只代表「提交成功」，NEVER 用它判断通知是否真的送达</b>——
 * 真实结果以 APP_PAY_SIGN_REQUEST / APP_TERMINATION_REQUEST 的
 * NOTIFY_STATUS、NOTIFY_RESULT 为准。</p>
 *
 * <p><b>2026-09-15 起本类是唯一定义点</b>：`pay-sign-server` 侧那份字段完全相同的
 * {@code paysign.model.response.CompensateNotifyRespDTO} 已删除，该模块直接用本类。
 * 此前两份并存、靠「改动 MUST 两侧同步」的口头约定维持，而 rpc 反序列化对缺字段是静默的 ——
 * <b>NEVER 再在业务模块新建同形副本</b>。</p>
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
