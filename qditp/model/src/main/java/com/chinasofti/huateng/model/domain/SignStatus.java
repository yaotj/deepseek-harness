package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public enum SignStatus {

    NOT_SIGNED,

    SIGNED,

    UNSIGNED,

    FAILED;

    private static final Map<SignStatus, Set<SignStatus>> ALLOWED = Map.of(
            NOT_SIGNED, EnumSet.of(SIGNED, FAILED),
            FAILED, EnumSet.of(SIGNED, NOT_SIGNED),
            SIGNED, EnumSet.of(UNSIGNED),
            UNSIGNED, EnumSet.of(NOT_SIGNED));


    public static SignStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (SignStatus s : values()) {
            if (s.name().equals(raw)) {
                return s;
            }
        }
        return null;
    }

    public boolean canTransitTo(SignStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
