package com.chinasofti.huateng.account.domain;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import java.util.List;

/**
 * 销户归档的判定规则 —— <b>「什么情况下允许把开户记录从原表物理删除」这条聚合不变量的唯一定义点</b>。
 */
public final class ArchiveDecision {
    /**
     * 判定结论。
     */
    public enum Outcome {
        /**
         * 全部开户记录已注销、且已无支付通道 ⇒ 可以归档。
         */
        ARCHIVE,
        /**
         * 该用户已无开户记录（前一次归档已完成，或从未开户）⇒ 幂等跳过。
         */
        NO_REG_INFO,
        /**
         * 仍有支付通道未解绑 ⇒ 不归档，等最后一个通道解绑时再来。
         */
        CHANNEL_REMAINING,
        /**
         * 存在未注销的开户记录（未走 IF8A-42，或注销后又重新开户）⇒ NEVER 归档。
         */
        NOT_ALL_CANCELED
    }

    /**
     * @param outcome  判定结论
     * @param ghostIds {@code DEL_YN} 既非有效也非已注销的行主键；仅在
     * {@link Outcome#NOT_ALL_CANCELED} 时可能非空，其余情形恒为空集
     */
    public record Result(Outcome outcome, List<Integer> ghostIds) {
        public boolean shouldArchive() {
            return outcome == Outcome.ARCHIVE;
        }

        /**
         * 命中幽灵态：该用户的归档将永久无法完成，且没有自愈路径（ADR-D41）。
         */
        public boolean hasGhostRows() {
            return !ghostIds.isEmpty();
        }
    }

    private ArchiveDecision() {
    }

    /**
     * 判定是否允许归档。
     *
     * @param regInfos          该用户的<b>全部</b>开户记录（不是「有效」口径 —— 归档发生在
     * {@code DEL_YN} 已置 0 之后，用有效口径查必然是空集）
     * @param remainingChannels 该用户剩余的支付通道数
     */
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
