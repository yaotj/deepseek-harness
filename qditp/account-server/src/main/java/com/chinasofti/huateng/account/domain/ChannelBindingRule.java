package com.chinasofti.huateng.account.domain;

import org.springframework.util.StringUtils;

/**
 * 支付通道绑定的入参不变量，<b>纯函数、无状态、不依赖任何 Bean</b>（第二个 domain 类，形态照
 * {@link ArchiveDecision}）。
 */
public final class ChannelBindingRule {
    /**
     * 钱包支付渠道编码，{@code APP_USER_PAY_CHANNEL.CHANNEL} 的取值之一。
     */
    public static final String WALLET_PAYMENT_CHANNEL = "0B";

    /**
     * 四个入口共用的空报文文案。
     */
    public static final String REQUEST_BODY_REQUIRED = "请求报文不能为空";

    private ChannelBindingRule() {
    }

    /**
     * 是否钱包渠道。
     */
    public static boolean isWallet(String channel) {
        return channel != null && WALLET_PAYMENT_CHANNEL.equals(channel.trim());
    }

    /**
     * IF8A-23 / 24 / 75 共用的「通道四要素」非空校验。
     *
     * @return 不通过时返回 retMsg 原文，通过返回 {@code null}
     */
    public static String validateBindingFields(String thirdUserId, String cardId, String cardType, String channel) {
        if (!StringUtils.hasText(thirdUserId)) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(cardId)) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(cardType)) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(channel)) {
            return "channel不能为空";
        }
        return null;
    }

    /**
     * IF8A-23 添加通道：四要素 + 钱包分支。
     *
     * @return 不通过时返回 retMsg 原文，通过返回 {@code null}
     */
    public static String validateAddBinding(String thirdUserId, String cardId, String cardType,
                                            String channel, String thirdPayId) {
        String msg = validateBindingFields(thirdUserId, cardId, cardType, channel);
        if (msg != null) {
            return msg;
        }
        if (isWallet(channel) && !StringUtils.hasText(thirdPayId)) {
            return "钱包支付渠道 thirdPayId 不能为空";
        }
        return null;
    }
}
