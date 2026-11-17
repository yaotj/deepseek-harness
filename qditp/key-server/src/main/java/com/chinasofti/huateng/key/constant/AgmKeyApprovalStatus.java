package com.chinasofti.huateng.key.constant;

import java.util.Map;
import java.util.Set;


public enum AgmKeyApprovalStatus {

    INIT("20010"),
    APPROVED("20020"),
    REJECTED("20030");

    private final String code;

    AgmKeyApprovalStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    private static final Map<AgmKeyApprovalStatus, Set<AgmKeyApprovalStatus>> ALLOWED = Map.of(
            INIT, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(),
            REJECTED, Set.of());

    public static AgmKeyApprovalStatus parseByCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (AgmKeyApprovalStatus st : values()) {
            if (st.code.equals(trimmed)) {
                return st;
            }
        }
        return null;
    }

    public boolean canTransitTo(AgmKeyApprovalStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
