package com.chinasofti.huateng.account.domain;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import java.util.List;

/**
 * 销户归档的判定规则 —— <b>「什么情况下允许把开户记录从原表物理删除」这条聚合不变量的唯一定义点</b>。
 *
 * <p>为什么要单独一个类：这条规则跨 {@code USER_ITP_REG_INFO} 与 {@code APP_USER_PAY_CHANNEL}
 * 两张表，此前散落在 {@code AccountArchiveServiceImpl.archiveIfLastChannelRemoved} 的三个提前
 * return 里，与「取锁」「写日志表」「删行」的编排代码混在一起，没有一处能被称为规则的定义点，
 * 也无法在不 mock 三个 mapper 的前提下测试。收敛后编排代码只负责取数与执行，判定在这里。
 * 形态照抄 {@code pay-sign-server} 的 {@code paysign/domain/SignStatusTransition}（ADR-D40）。
 *
 * <p><b>本类 NEVER 依赖任何 mapper / Spring Bean</b>：它是纯函数，取数由调用方负责。
 * 一旦让它自己查库，就又回到了「规则与编排缠在一起、必须起 Spring 才能测」的状态。
 *
 * <p><b>NEVER 把 {@link Outcome#NOT_ALL_CANCELED} 与幽灵行合并成一个结果</b>：
 * 「用户真的还有未注销的票卡」是正常业务分支（打 INFO 即可），
 * 「{@code DEL_YN} 既非 1 也非 0」是数据缺陷、会让该用户永久无法归档（MUST 打 WARN 并留主键）。
 * 两者都表现为 {@code allMatch(isCanceled)} 为 false，日志里混在一起就再也分不出来。
 */
public final class ArchiveDecision {

    /** 判定结论。除 {@link #ARCHIVE} 外一律不归档。 */
    public enum Outcome {
        /** 全部开户记录已注销、且已无支付通道 ⇒ 可以归档。 */
        ARCHIVE,
        /** 该用户已无开户记录（前一次归档已完成，或从未开户）⇒ 幂等跳过。 */
        NO_REG_INFO,
        /** 仍有支付通道未解绑 ⇒ 不归档，等最后一个通道解绑时再来。 */
        CHANNEL_REMAINING,
        /** 存在未注销的开户记录（未走 IF8A-42，或注销后又重新开户）⇒ NEVER 归档。 */
        NOT_ALL_CANCELED
    }

    /**
     * @param outcome  判定结论
     * @param ghostIds {@code DEL_YN} 既非有效也非已注销的行主键；仅在
     *                 {@link Outcome#NOT_ALL_CANCELED} 时可能非空，其余情形恒为空集
     */
    public record Result(Outcome outcome, List<Integer> ghostIds) {

        public boolean shouldArchive() {
            return outcome == Outcome.ARCHIVE;
        }

        /** 命中幽灵态：该用户的归档将永久无法完成，且没有自愈路径（ADR-D41）。 */
        public boolean hasGhostRows() {
            return !ghostIds.isEmpty();
        }
    }

    private ArchiveDecision() {
    }

    /**
     * 判定是否允许归档。
     *
     * <p>三个条件的<b>顺序不可调换</b>，与调用方的取数顺序对应：先确认有记录（否则后两步无意义），
     * 再看通道是否清空（这一步在调用方是持锁后的 count），最后才逐行看注销状态。
     *
     * @param regInfos          该用户的<b>全部</b>开户记录（不是「有效」口径 —— 归档发生在
     *                          {@code DEL_YN} 已置 0 之后，用有效口径查必然是空集）
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
