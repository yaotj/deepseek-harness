package com.chinasofti.huateng.facepay.domain;

public enum F2fOrderRefundStatus {

    NONE,
    PARTIAL,
    SUCCESS;

    public static F2fOrderRefundStatus parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        for (F2fOrderRefundStatus st : values()) {
            if (st.name().equals(trimmed)) {
                return st;
            }
        }
        return null;
    }

    public static boolean refunded(String raw) {
        F2fOrderRefundStatus st = parseOrNull(raw);
        return st == PARTIAL || st == SUCCESS;
    }
}
