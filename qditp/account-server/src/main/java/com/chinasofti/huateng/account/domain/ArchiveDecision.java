package com.chinasofti.huateng.account.domain;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import java.util.List;


public final class ArchiveDecision {

    public enum Outcome {

        ARCHIVE,

        NO_REG_INFO,

        CHANNEL_REMAINING,

        NOT_ALL_CANCELED
    }


    public record Result(Outcome outcome, List<Integer> ghostIds) {
        public boolean shouldArchive() {
            return outcome == Outcome.ARCHIVE;
        }


        public boolean hasGhostRows() {
            return !ghostIds.isEmpty();
        }
    }

    private ArchiveDecision() {
    }


    public static Result decide(List<UserItpRegInfo> regInfos, int remainingChannels) {
        if (regInfos == null || regInfos.isEmpty()) {
            return new Result(Outcome.NO_REG_INFO, List.of());
        }
        if (remainingChannels > 0) {
            return new Result(Outcome.CHANNEL_REMAINING, List.of());
        }
        boolean allCanceled = regInfos.stream().allMatch(UserItpRegInfo::isCanceled);
        if (allCanceled) {
            return new Result(Outcome.ARCHIVE, List.of());
        }
        List<Integer> ghostIds = regInfos.stream()
                .filter(info -> !info.isActive() && !info.isCanceled())
                .map(UserItpRegInfo::getId)
                .toList();
        return new Result(Outcome.NOT_ALL_CANCELED, ghostIds);
    }
}
