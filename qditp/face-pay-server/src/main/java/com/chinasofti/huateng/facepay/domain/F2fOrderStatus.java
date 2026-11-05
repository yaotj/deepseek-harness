package com.chinasofti.huateng.facepay.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum F2fOrderStatus {

    CREATED,
    PAYING,
    PAID,
    FULFILLED,
    FULFILL_FAILED,
    TOPUP_SUSPECT,
    PAY_FAILED,
    EXPIRED,
    REFUNDING,
    REFUNDED,
    CANCELED;

    private static final Map<F2fOrderStatus, Set<F2fOrderStatus>> ALLOWED = Map.of(
            CREATED, EnumSet.of(PAYING, PAID, PAY_FAILED, EXPIRED),
            PAYING, EnumSet.of(PAID, PAY_FAILED, EXPIRED),
            PAID, EnumSet.of(FULFILLED, FULFILL_FAILED, REFUNDING),
            FULFILLED, EnumSet.of(REFUNDING),
            FULFILL_FAILED, EnumSet.of(REFUNDING),
            REFUNDING, EnumSet.of(REFUNDED),
            PAY_FAILED, EnumSet.noneOf(F2fOrderStatus.class),
            EXPIRED, EnumSet.noneOf(F2fOrderStatus.class),
            REFUNDED, EnumSet.noneOf(F2fOrderStatus.class),
            CANCELED, EnumSet.noneOf(F2fOrderStatus.class));

    public static final List<String> PENDING = names(CREATED, PAYING);

    public static final List<String> REFUNDABLE = names(PAID, FULFILLED, FULFILL_FAILED);

    public static final List<String> FAILED_LIKE = names(PAY_FAILED, EXPIRED, CANCELED);

    public static final List<String> FULFILLABLE = names(PAID);

    public static F2fOrderStatus parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        for (F2fOrderStatus st : values()) {
            if (st.name().equals(trimmed)) {
                return st;
            }
        }
        return null;
    }

    public boolean canTransitTo(F2fOrderStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    private static List<String> names(F2fOrderStatus... statuses) {
        return List.of(java.util.Arrays.stream(statuses).map(Enum::name).toArray(String[]::new));
    }
}
