package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public enum AlipayTerminationStatus {

    PENDING,

    COMPLETED,

    FAIL;

    private static final Map<AlipayTerminationStatus, Set<AlipayTerminationStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(COMPLETED, FAIL),
            COMPLETED, EnumSet.noneOf(AlipayTerminationStatus.class),
            FAIL, EnumSet.noneOf(AlipayTerminationStatus.class));

    public static AlipayTerminationStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (AlipayTerminationStatus status : values()) {
            if (status.name().equals(raw)) {
                return status;
            }
        }
        return null;
    }

    public boolean canTransitTo(AlipayTerminationStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    public boolean isActionable() {
        return this == PENDING;
    }
}
