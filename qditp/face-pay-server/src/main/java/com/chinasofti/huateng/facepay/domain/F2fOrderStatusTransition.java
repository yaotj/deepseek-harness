package com.chinasofti.huateng.facepay.domain;

import java.util.function.Supplier;

public final class F2fOrderStatusTransition {

    private F2fOrderStatusTransition() {
    }

    public enum Outcome {
        DONE,
        IDEMPOTENT,
        CONFLICT
    }

    public record Result(Outcome outcome, String observedStatus) {

        public boolean conflict() {
            return outcome == Outcome.CONFLICT;
        }

        public boolean settled() {
            return outcome != Outcome.CONFLICT;
        }
    }

    public static Result classify(int updatedRows, F2fOrderStatus target,
                                  Supplier<String> currentStatusLoader) {
        if (updatedRows > 0) {
            return new Result(Outcome.DONE, null);
        }
        String raw = currentStatusLoader.get();
        F2fOrderStatus current = F2fOrderStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
