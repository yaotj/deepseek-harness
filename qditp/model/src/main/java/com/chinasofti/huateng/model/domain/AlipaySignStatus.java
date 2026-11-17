package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public enum AlipaySignStatus {

    SIGNED,

    TERMINATED;

    private static final Map<AlipaySignStatus, Set<AlipaySignStatus>> ALLOWED = Map.of(
            SIGNED, EnumSet.of(TERMINATED),
            TERMINATED, EnumSet.noneOf(AlipaySignStatus.class));

    public static AlipaySignStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (AlipaySignStatus status : values()) {
            if (status.name().equals(raw)) {
                return status;
            }
        }
        return null;
    }

    public boolean canTransitTo(AlipaySignStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
