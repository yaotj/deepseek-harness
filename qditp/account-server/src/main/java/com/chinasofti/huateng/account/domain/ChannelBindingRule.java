package com.chinasofti.huateng.account.domain;

import org.springframework.util.StringUtils;


public final class ChannelBindingRule {

    public static final String WALLET_PAYMENT_CHANNEL = "0B";


    public static final String REQUEST_BODY_REQUIRED = "请求报文不能为空";

    private ChannelBindingRule() {
    }


    public static boolean isWallet(String channel) {
        return channel != null && WALLET_PAYMENT_CHANNEL.equals(channel.trim());
    }


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
