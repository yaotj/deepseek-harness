package com.chinasofti.huateng.alipay.paysign.domain;

import com.chinasofti.huateng.model.domain.AlipaySignStatus;

import java.util.function.Supplier;


public final class AlipaySignStatusTransition {

    private AlipaySignStatusTransition() {
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

        public boolean reachedTarget() {
            return outcome != Outcome.CONFLICT;
        }
    }


    public static Result classify(int updatedRows, AlipaySignStatus target, Supplier<String> currentStatusLoader) {
        if (updatedRows > 0) {
            return new Result(Outcome.DONE, null);
        }
        String raw = currentStatusLoader.get();
        AlipaySignStatus current = AlipaySignStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
