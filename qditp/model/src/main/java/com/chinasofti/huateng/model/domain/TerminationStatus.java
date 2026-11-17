package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public enum TerminationStatus {

    PENDING,

    SCANNING,

    SUCCESS,

    FAILED;

    private static final Map<TerminationStatus, Set<TerminationStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(SCANNING, FAILED),
            SCANNING, EnumSet.of(SUCCESS, FAILED, PENDING),
            FAILED, EnumSet.of(PENDING),
            SUCCESS, EnumSet.noneOf(TerminationStatus.class));


    public static TerminationStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (TerminationStatus s : values()) {
            if (s.name().equals(raw)) {
                return s;
            }
        }
        return null;
    }

    public boolean canTransitTo(TerminationStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }


    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }


    public boolean isNotifiable() {
        return this == SUCCESS || this == FAILED;
    }
}
