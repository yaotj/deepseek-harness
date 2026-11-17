package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.SignStatus;

import java.util.function.Supplier;

public final class SignStatusTransition {

    private SignStatusTransition() {
    }

    public enum Outcome {
        DONE,
        IDEMPOTENT,
        CONFLICT
    }

    public record Result(Outcome outcome, String observedStatus) {

        public boolean isConflict() {
            return outcome == Outcome.CONFLICT;
        }
    }


    public static Result classify(int updatedRows, SignStatus target, Supplier<String> currentStatusLoader) {
        if (updatedRows > 0) {
            return new Result(Outcome.DONE, null);
        }
        String raw = currentStatusLoader.get();
        SignStatus current = SignStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
