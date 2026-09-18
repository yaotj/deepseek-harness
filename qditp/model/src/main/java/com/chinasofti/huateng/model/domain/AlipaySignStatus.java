package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 支付宝出行签约状态机。载体 {@code ALIPAY_SIGN_INFO.SIGN_STATUS}。
 *
 * <p>迁移图只有一条：{@code SIGNED -> TERMINATED}。签约行是「插入即已签约」（协议号由渠道侧
 * 先建好再登记到我方），因此没有 pay-sign 侧那种 {@code NOT_SIGNED} 起点，也没有失败态。</p>
 *
 * <p><b>NEVER 与 {@link SignStatus} 归一</b>（ADR-D124）：两者是不同渠道的两份词表 ——
 * pay-sign 侧是 {@code NOT_SIGNED / SIGNED / UNSIGNED / FAILED}，本渠道库里只存过
 * {@code SIGNED / TERMINATED}。合并枚举会让某一侧写出库里从来没有过的值，而比较点全是
 * 字符串等值，错值不报错、只会静默匹配不上。</p>
 *
 * <p><b>NEVER 允许 {@code TERMINATED -> SIGNED}</b>：已销卡的协议被迟到的签约登记覆盖，
 * 会让扣款链路重新按这条协议号去支付中心扣费，而渠道侧协议早已注销。重新签约 MUST 走
 * 新的 {@code AGREEMENT_CODE} 插一行新记录。</p>
 */
public enum AlipaySignStatus {

    /** 已签约。免密扣款与销卡的唯一合法前置态。 */
    SIGNED,

    /** 已销卡。终态，NEVER 回到 SIGNED。 */
    TERMINATED;

    private static final Map<AlipaySignStatus, Set<AlipaySignStatus>> ALLOWED = Map.of(
            SIGNED, EnumSet.of(TERMINATED),
            TERMINATED, EnumSet.noneOf(AlipaySignStatus.class));

    /** 宽松解析：库内可能是 NULL 或历史脏值，解析不出来返回 {@code null} 而不抛异常。 */
    public static AlipaySignStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (AlipaySignStatus status : values()) {
            if (status.name().equals(raw)) {
                return status;
            }
        }
        return null;
    }

    /** 只做快速失败与错误提示，NEVER 当作并发保证 —— 并发保证是 mapper 里那条 CAS 的 WHERE。 */
    public boolean canTransitTo(AlipaySignStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 无任何合法后继即终态。 */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
