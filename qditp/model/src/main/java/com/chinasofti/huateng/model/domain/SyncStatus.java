package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public enum SyncStatus {

    PENDING,

    SUCCESS,

    FAILED;

    private static final Map<SyncStatus, Set<SyncStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(SUCCESS, FAILED),
            FAILED, EnumSet.of(SUCCESS, FAILED),
            SUCCESS, EnumSet.noneOf(SyncStatus.class));


    public static SyncStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (SyncStatus s : values()) {
            if (s.name().equals(raw)) {
                return s;
            }
        }
        return null;
    }

    public boolean isCompensable() {
        return this == PENDING || this == FAILED;
    }

    public boolean canTransitTo(SyncStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
