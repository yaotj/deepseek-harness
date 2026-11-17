package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.TerminationStatus;

import java.util.function.Supplier;

public final class TerminationStatusTransition {

    private TerminationStatusTransition() {
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

        public boolean isDone() {
            return outcome == Outcome.DONE;
        }
    }


    public static Result classify(int updatedRows, TerminationStatus target, Supplier<String> currentStatusLoader) {
        if (updatedRows > 0) {
            return new Result(Outcome.DONE, null);
        }
        String raw = currentStatusLoader.get();
        TerminationStatus current = TerminationStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
