package com.chinasofti.huateng.account.domain;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import org.springframework.util.StringUtils;

/**
 * 换手机号的前置条件，<b>纯函数、无状态、不依赖任何 Bean</b>（形态照 {@link ArchiveDecision}）。
 */
public final class PhoneChangeRule {
    /**
     * 前置条件的三种结论。
     */
    public enum Precondition {
        /**
         * 没有有效开户记录，换号失败。
         */
        NO_ACTIVE_USER,
        /**
         * 新旧号相同，不需要改库、也不需要向支付域投递，对 APP 仍返成功。
         */
        UNCHANGED,
        /**
         * 可以改库并在提交后向支付域投递。
         */
        PROCEED
    }

    private PhoneChangeRule() {
    }

    /**
     * 入口级必填校验：{@code thirdUserId} 与新号都 MUST 非空白。
     */
    public static boolean hasRequiredFields(String thirdUserId, String newMsisdn) {
        return StringUtils.hasText(thirdUserId) && StringUtils.hasText(newMsisdn);
    }

    /**
     * 判定是否需要执行换号。
     *
     * @param regInfo   有效开户记录，允许为 {@code null}（表示查不到）
     * @param newMsisdn 新手机号，调用方 MUST 已 trim 且非空
     */
    public static Precondition decide(UserItpRegInfo regInfo, String newMsisdn) {
        if (regInfo == null) {
            return Precondition.NO_ACTIVE_USER;
        }
        String oldMsisdn = regInfo.getMsisdn();
        if (oldMsisdn != null && oldMsisdn.equals(newMsisdn)) {
            return Precondition.UNCHANGED;
        }
        return Precondition.PROCEED;
    }
}
