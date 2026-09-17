package com.chinasofti.huateng.paysign.domain;

/** {@code APP_TERMINATION_REQUEST.FAIL_REASON} 这一列的读写约定。 */
public final class TerminationFailReason {

    /** 人工核对标记，运维按它检索。 */
    public static final String MANUAL_REVIEW_MARK = "[需人工核对:解约结果矛盾]";

    /** 内部说明与原始失败原因之间的分隔符，{@link #stripManualMark} 靠它切回原值。 */
    private static final String NOTE_END = "|原因:";

    private TerminationFailReason() {
    }

    /** 组装「解约已成功但库内是 FAILED」的内部说明。返回值 MUST 原样作为 mapper 的 {@code manualNote}。 */
    public static String successConflictNote() {
        return MANUAL_REVIEW_MARK
                + "支付平台已答复解约成功但库内已是 FAILED，MUST 先查支付中心 queryResult 再订正。"
                + NOTE_END;
    }

    /** 组装「本次回调答复失败但库内已是 SUCCESS」的内部说明。 */
    public static String failureConflictNote() {
        return MANUAL_REVIEW_MARK
                + "本次回调答复解约失败但库内已是 SUCCESS，两条回调结论相反，MUST 人工判定以哪条为准。"
                + NOTE_END;
    }

    /**
     * 剥掉内部说明，返回可以发给 APP 的原始失败原因。
     *
     * @param rawFailReason 库里的原值，可为 {@code null}
     * @return 未标记时原样返回；已标记时返回标记之后的原始原因；标记存在但分隔符缺失时返回空串
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
