package com.chinasofti.huateng.gatetxnpay.constant;

/**
 * {@code GATE_TXN_PAY.DISCOUNT_CALC_STATUS} 的唯一取值来源。
 *
 * <p>这是本模块的<b>第三套状态词汇</b>（另两套是 {@link DebitStatus} 与
 * {@code SUPPLEMENT_ORDER.PAY_STATUS} —— 后者<b>尚未收口</b>，仍是 `SupplementOrderServiceImpl` /
 * `SupplementConvergeService` 里的裸字面量，按用户 2026-09-14 裁决暂不动）。</p>
 * 只有 {@code SUCCESS} 与那两套同形，<b>NEVER 混用</b> —— 它表达的是「算价/优惠这一步算成没算成」，
 * 与「钱扣没扣成」完全无关：一笔订单可以 {@code DISCOUNT_CALC_STATUS='FALLBACK'} 同时
 * {@code DEBIT_STATUS='SUCCESS'}。</p>
 *
 * <p><b>列宽已加宽到 32 字符</b>（`sql/gate-txn-pay-discount-calc-status-widen-migration.sql`）——
 * 原 16 字符装不下 {@link #OFFLINE_FARE_PENDING} 的 20 字符，会在运行时报 `ORA-12899`。
 * <b>新增取值 MUST 先核对列宽</b>。</p>
 *
 * <h3>mapper XML 对照（SQL 字面量无法引用 Java 常量）</h3>
 * {@code DISCOUNT_CALC_STATUS = 'OFFLINE_FARE_PENDING'} 出现在 `GateTxnPayMapper.xml` 三处
 * （离线码待重算的扫表判据与两条 CAS 的 WHERE）。改这个值 <b>MUST</b> 同步那三处，
 * 漏改即「补偿任务扫不到、金额永远是 0」，且编译与单测都发现不了。
 *
 * <p>入库与比较 <b>MUST</b> 用 {@link #code()}，<b>NEVER</b> 用 {@link #name()}。</p>
 */
public enum DiscountCalcStatus {
    /** 优惠算好了。 */
    SUCCESS("SUCCESS"),
    /** 本笔不适用优惠（非钱包渠道 / 同行票 / 免扣费等），不是失败。 */
    SKIPPED("SKIPPED"),
    /** 取数或算价失败，已退化为不打折的原价，交易照常放行。 */
    FALLBACK("FALLBACK"),
    /** 离线码入库时拿不到金额，落痕等 {@code OfflineFareRecoveryProcessor} 重算。 */
    OFFLINE_FARE_PENDING("OFFLINE_FARE_PENDING");

    private final String code;

    DiscountCalcStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean is(String value) {
        return code.equals(value);
    }
}
