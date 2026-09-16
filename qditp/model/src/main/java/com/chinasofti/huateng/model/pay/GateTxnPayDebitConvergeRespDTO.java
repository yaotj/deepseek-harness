package com.chinasofti.huateng.model.pay;

/**
 * 补款收敛原过闸订单扣费状态的响应。
 *
 * <p><b>本响应的关键设计是 {@link #converged} 与 {@link #debitStatus} 一起返回</b>，
 * 让调用方一次拿到「本次有没有真的改到行」+「这笔订单现在的权威状态」两个判据，
 * 从而在**一次调用**里分出三支（成功收敛 / 重复扣款待退款 / 需人工核对）。
 * 收口前 face-pay 是「先 UPDATE、0 行时再 SELECT 回查」两次跨域访问，现在读写都收在对端了。</p>
 *
 * <p><b>NEVER 只看 {@code retCode} 就判成功</b>：`retCode=0000` 只表示对端受理并处理完毕，
 * 真正的业务分支要看 `converged`：</p>
 * <ul>
 *   <li>{@code retCode=0000} + {@code converged=true} —— 本次把原订单从欠费态推进到 `SUCCESS`，
 *       调用方把补款明细标为已结清。</li>
 *   <li>{@code retCode=0000} + {@code converged=false} + {@code debitStatus=SUCCESS} ——
 *       原订单**早已被别人收敛**（通常是先到的另一张补款单）。钱已实收但行程早已结清，
 *       本单属**重复支付、待退款**，调用方 MUST 标失败并留「重复支付待退款」，
 *       <b>NEVER 当成幂等成功悄悄跳过</b>。</li>
 *   <li>{@code retCode} 非 {@code 0000}（或 {@code converged=false} 且 {@code debitStatus} 不是
 *       {@code SUCCESS}）—— 订单不存在或状态不在收敛白名单内，属**业务拒绝、重试无用**，
 *       MUST 一次即终态并留人工核对线索。</li>
 * </ul>
 *
 * <p><b>「网络不可达」不在本 DTO 的表达范围内</b>：那种情况下调用方拿不到响应（`rpc` 侧抛异常），
 * 由调用方 catch 后**不改任何本地状态**、留给补偿任务下一轮重入
 * （face-pay 侧是 `SupplementOrderCloseProcessor.converge`，cron `0 *&#47;5 * * * ?`）。
 * 这正是 {@code RpcOutcome} 三分支里 {@code Unreachable} 的位置，
 * <b>NEVER 把它和上面第三支（业务拒绝）合并处理</b> —— 前者该重试、后者重试一万次也不会变。</p>
 */
public class GateTxnPayDebitConvergeRespDTO {

    /** 业务码，{@code 0000} 表示对端已处理完毕（不代表本次改了行，见 {@link #converged}）。 */
    private String retCode;

    /** 业务文案，仅用于日志与工单，<b>NEVER 用它做分支判断</b>。 */
    private String retMsg;

    /** 回显原过闸订单号，便于调用方在批量场景下对齐请求与响应。 */
    private String origOrderNo;

    /**
     * 本次调用是否真的把原订单推进到了 {@code SUCCESS}（UPDATE 影响行数 &gt; 0）。
     *
     * <p>用 {@code Boolean} 而非 {@code boolean}：Fastjson2 宽松模式下字段缺失会得到 {@code null}，
     * 保留 null 能让「对端是旧版本、根本没这个字段」暴露成 NPE 或显式判空，
     * 而基本类型会静默默认成 {@code false} —— 那会把「成功收敛」误判成「重复支付待退款」。
     * 调用方 MUST 显式判 {@code Boolean.TRUE.equals(converged)}。</p>
     */
    private Boolean converged;

    /**
     * 处理后原订单的权威 {@code DEBIT_STATUS}（取自对端本次读到的值）。
     *
     * <p>{@code converged=false} 时，调用方**只能**靠这个字段区分「已被别人收敛（SUCCESS）」
     * 与「状态不允许收敛（其余值）」，因此对端 MUST 始终回填它，
     * <b>NEVER 因为「本次没改行」就省略</b>。订单不存在时为 {@code null}。</p>
     */
    private String debitStatus;

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

    public String getOrigOrderNo() {
        return origOrderNo;
    }

    public void setOrigOrderNo(String origOrderNo) {
        this.origOrderNo = origOrderNo;
    }

    public Boolean getConverged() {
        return converged;
    }

    public void setConverged(Boolean converged) {
        this.converged = converged;
    }

    public String getDebitStatus() {
        return debitStatus;
    }

    public void setDebitStatus(String debitStatus) {
        this.debitStatus = debitStatus;
    }

    @Override
    public String toString() {
        return "GateTxnPayDebitConvergeRespDTO{retCode='" + retCode + "', retMsg='" + retMsg
                + "', origOrderNo='" + origOrderNo + "', converged=" + converged
                + ", debitStatus='" + debitStatus + "'}";
    }
}
