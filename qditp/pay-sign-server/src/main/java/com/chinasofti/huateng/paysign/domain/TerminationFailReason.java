package com.chinasofti.huateng.paysign.domain;

/**
 * {@code APP_TERMINATION_REQUEST.FAIL_REASON} 这一列的读写约定。
 *
 * <p><b>存在理由是一个已发生的缺陷</b>（2026-09-12，ADR-D47 当轮引入、同日发现）：该列**同时**服务两个
 * 互不相干的读者 —— 运维排查（要看内部说明）与 APP 解约失败通知的 {@code terminationResultMsg}
 * （**绝不能看到内部说明**）。ADR-D47 把「需人工核对」标记前置拼进这一列时只考虑了前者，于是
 * {@code AppNotifyServiceImpl.asyncRetryTerminationNotify} 的补偿重发会把运维文案发给终端用户。
 *
 * <p>因此写格式与读格式 **MUST 放在同一个类里**：拆开放两处就是这次缺陷的成因。
 * 新增任何 {@code FAIL_REASON} 的读者前 **MUST 先判断它属于哪一类读者**：
 * 面向运维的直接读原值，面向外部的一律先过 {@link #stripManualMark(String)}。
 */
public final class TerminationFailReason {

    /**
     * 人工核对标记，运维按它检索：
     * {@code WHERE TERMINATION_STATUS IN ('SUCCESS','FAILED') AND FAIL_REASON LIKE '%需人工核对:解约结果矛盾%'}。
     *
     * <p>**NEVER 改这个字面量**：它同时是 mapper 里 {@code INSTR} 幂等判据的入参、运维检索关键字，
     * 以及 {@code TerminationFailReasonTest} 钉住的常量。
     */
    public static final String MANUAL_REVIEW_MARK = "[需人工核对:解约结果矛盾]";

    /**
     * 内部说明与原始失败原因之间的分隔符，{@link #stripManualMark} 靠它切回原值。
     *
     * <p>**MUST 保证内部说明本身不含这个串**，否则切点会落错位置（本类的两个 note 方法是唯一写入方，
     * 由测试保证）。
     */
    private static final String NOTE_END = "|原因:";

    private TerminationFailReason() {
    }

    /**
     * 组装「解约已成功但库内是 FAILED」的内部说明。返回值 MUST 原样作为 mapper 的 {@code manualNote}
     * 传入 —— SQL 会把它前置拼到原 {@code FAIL_REASON} 之前。
     */
    public static String successConflictNote() {
        return MANUAL_REVIEW_MARK
                + "支付平台已答复解约成功但库内已是 FAILED，MUST 先查支付中心 queryResult 再订正。"
                + NOTE_END;
    }

    /**
     * 组装「本次回调答复失败但库内已是 SUCCESS」的内部说明。
     *
     * <p>与 {@link #successConflictNote()} 刻意分成两个方法而不是传参：两者的人工处置口径不同，
     * 合成一个后运维无法从表里区分是哪一种矛盾。
     */
    public static String failureConflictNote() {
        return MANUAL_REVIEW_MARK
                + "本次回调答复解约失败但库内已是 SUCCESS，两条回调结论相反，MUST 人工判定以哪条为准。"
                + NOTE_END;
    }

    /**
     * 剥掉内部说明，返回可以发给 APP 的原始失败原因。
     *
     * <p>**面向外部的读者 MUST 调本方法**，NEVER 直接把 {@code FAIL_REASON} 原值发出去。
     *
     * @param rawFailReason 库里的原值，可为 {@code null}
     * @return 未标记时原样返回；已标记时返回标记之后的原始原因；标记存在但分隔符缺失时返回空串
     *         （宁可让下游回落到默认文案，也 NEVER 把内部说明发出去）
     */
    public static String stripManualMark(String rawFailReason) {
        if (rawFailReason == null || !rawFailReason.startsWith(MANUAL_REVIEW_MARK)) {
            return rawFailReason;
        }
        int sep = rawFailReason.indexOf(NOTE_END);
        if (sep < 0) {
            return "";
        }
        return rawFailReason.substring(sep + NOTE_END.length());
    }
}
