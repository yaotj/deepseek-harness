package com.chinasofti.huateng.paysign.domain;

public final class TerminationFailReason {

    public static final String MANUAL_REVIEW_MARK = "[需人工核对:解约结果矛盾]";

    private static final String NOTE_END = "|原因:";

    private TerminationFailReason() {
    }

    public static String successConflictNote() {
        return MANUAL_REVIEW_MARK
                + "支付平台已答复解约成功但库内已是 FAILED，MUST 先查支付中心 queryResult 再订正。"
                + NOTE_END;
    }

    public static String failureConflictNote() {
        return MANUAL_REVIEW_MARK
                + "本次回调答复解约失败但库内已是 SUCCESS，两条回调结论相反，MUST 人工判定以哪条为准。"
                + NOTE_END;
    }


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
