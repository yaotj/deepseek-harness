package com.chinasofti.huateng.model.pay;

/**
 * 补款支付成功后请求 gate-txn-pay 收敛原过闸订单扣费状态。
 *
 * <p><b>存在的原因是消除一处跨域写表</b>：2026-09-16 之前 face-pay-server 自己持有一份
 * {@code UPDATE GATE_TXN_PAY SET DEBIT_STATUS='SUCCESS'}（`facepay/mapper/GateTxnPayMapper.xml`），
 * 而这张表的 owner 是 gate-txn-pay-server。同一列的写入语义被两个模块各持一份，
 * 违反 {@code docs/domain} 的「热路径写入定 owner」判据；那份副本已随本接口上线整段删除，
 * <b>NEVER 在 face-pay 侧加回任何对 {@code GATE_TXN_PAY} 的写语句</b>。</p>
 *
 * <p><b>为什么不复用已有的 {@code /ci/gateTxnPay/syncDebitStatus}</b>（同样是「把扣费状态收敛到终态」）：
 * 那条端点在「UPDATE 影响 0 行但订单已是同一终态」时**也返 `0000`**（按幂等成功处理），
 * 把「本次真的改了行」与「早已被别人收敛过」压成同一个结果。而补款链路恰恰必须区分这两者 ——
 * 后者意味着**同一笔行程被两张补款单各支付了一次，属重复扣款、需要退款**
 * （见 face-pay 的 {@code SupplementPayCenterFlow.settleSuccess}）。复用它会让重复扣款静默漏判，
 * 因此本请求对应一个**独立端点 + 独立响应契约**（响应 MUST 带 `converged` 与当前 `debitStatus`）。</p>
 *
 * <p><b>收敛白名单与 `syncDebitStatus` 那条不同，这是有意的</b>：本接口允许
 * {@code INIT / PROCESSING / RETRY / FAIL} → {@code SUCCESS}，**多一个 `FAIL`**。
 * 理由是它必须与补款下单校验的「欠费可补」口径一致 —— 能下单的欠费状态就必须能收敛，
 * 否则 `FAIL` 单会「放行下单、补款成功、却收敛不了」，钱收了账没平。
 * 用户 2026-09-16 明确裁决按此口径落地（选项 A：行为与收口前逐笔一致）。
 * <b>要统一成不含 `FAIL` 属业务规则变更，MUST 先与业务确认「FAIL 单能否补款」，NEVER 自行收窄。</b></p>
 */
public class GateTxnPayDebitConvergeReqDTO {

    /**
     * 原过闸扣费订单号，即 {@code GATE_TXN_PAY.ORDER_NO}，也是补款明细里的 {@code ORIG_ORDER_NO}。
     */
    private String origOrderNo;

    /**
     * 原订单交易日期 {@code GATE_TXN_PAY.TXN_DATE}（{@code yyyyMMdd}）。
     *
     * <p><b>MUST 传</b>：`GATE_TXN_PAY` 是按 `TXN_DATE` 的月分区表，带上它才能分区裁剪；
     * 而且它和 `ORDER_NO` 一起构成收敛语句的 WHERE，缺失会导致更新命中不到行。
     * 调用方从补款明细的 {@code ORIG_TXN_DATE} 取，<b>NEVER 用当天日期替代</b> ——
     * 补款可能发生在行程之后的任意一天。</p>
     */
    private String txnDate;

    /**
     * 回写到 {@code GATE_TXN_PAY.REMARK} 的收敛来源，便于人工追溯是哪张补款单结清的。
     *
     * <p>调用方按 {@code "补款单号:" + 补款单号} 组织，只作为人读线索，
     * <b>NEVER 让任何代码分支去解析它</b>。</p>
     */
    private String remark;

    public String getOrigOrderNo() {
        return origOrderNo;
    }

    public void setOrigOrderNo(String origOrderNo) {
        this.origOrderNo = origOrderNo;
    }

    public String getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(String txnDate) {
        this.txnDate = txnDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "GateTxnPayDebitConvergeReqDTO{origOrderNo='" + origOrderNo
                + "', txnDate='" + txnDate + "', remark='" + remark + "'}";
    }
}
