package com.chinasofti.huateng.gatetxnpay.constant;

/**
 * `GATE_TXN_PAY.DEBIT_STATUS` 的**唯一取值来源**。
 *
 * <p>本枚举只覆盖 {@code DEBIT_STATUS} 这一列。<b>NEVER</b> 拿它去表示
 * {@code DISCOUNT_CALC_STATUS}（那一列的取值是 {@code SUCCESS / SKIPPED / FALLBACK /
 * OFFLINE_FARE_PENDING}，与本列只有 {@code SUCCESS} 同形）或 {@code SUPPLEMENT_ORDER.PAY_STATUS}
 * （那是另一台状态机，见 {@code docs/domain/state-machines.md}）—— 两列同形不同义，
 * 混用等于把两台状态机的白名单接到一起。</p>
 *
 * <p>存在的理由不是「枚举比字符串优雅」，而是这五个值此前散在
 * {@code GateTxnPayServiceImpl} / {@code PaySignInitiator} / {@code SupplementConvergeService}
 * 三个类里各写一份，而 AGENTS.md §2.2.1 要求「改状态值 MUST 全局 grep」——
 * 靠人记的规则迟早漏一处，而漏的那一处不报错、只在生产表现为「状态永远收敛不了」。</p>
 *
 * <p>入库与比较 <b>MUST</b> 用 {@link #code()} / {@link #is(String)}，
 * <b>NEVER</b> 用 {@link #name()}：虽然当前两者字面相同，但 {@code name()} 一旦被
 * 重命名就会静默改掉落库值。</p>
 */
public enum DebitStatus {
    /** 已落单、尚未发起扣款（含离线码待重算态）。 */
    INIT("INIT"),
    /** 已向支付域发起扣款、等回调。 */
    PROCESSING("PROCESSING"),
    /** 发起失败或无响应，留给补偿重试。 */
    RETRY("RETRY"),
    /** 终态：已收到钱（免扣费与日票交易落单即此态）。 */
    SUCCESS("SUCCESS"),
    /** 终态：扣款失败。 */
    FAIL("FAIL");

    private final String code;

    DebitStatus(String code) {
        this.code = code;
    }

    /** 落库值。入库与 mapper 参数 MUST 用本方法，NEVER 用 {@code name()}。 */
    public String code() {
        return code;
    }

    public boolean is(String value) {
        return code.equals(value);
    }

    /**
     * 允许运营重试免密扣款的前置状态白名单。
     *
     * <p>白名单而非黑名单是 AGENTS.md §5.2 的硬规则：写成「非终态即可重试」会把
     * {@link #PROCESSING}（扣款在途）也放进来，等于对同一笔欠费同时开两条扣款路径。</p>
     */
    public static boolean isRetryable(String value) {
        return RETRY.is(value) || INIT.is(value);
    }

    /**
     * 允许发起退款的前置状态白名单。
     *
     * <p>{@link #PROCESSING} 在列内是有意的：支付中心可能已扣款成功但回调还没到，
     * 这种在途单也要能退。</p>
     */
    public static boolean isRefundable(String value) {
        return SUCCESS.is(value) || PROCESSING.is(value);
    }
}
