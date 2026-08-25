package com.chinasofti.huateng.model.app;

import java.util.Map;

/**
 * APP 入参卡类型到发卡卡类型的映射。
 */
public final class CardTypeMapping {
    public static final String AI_SHAN_DONG_CARD_TYPE = "044A";

    private static final Map<String, String> ISSUE_CARD_TYPES = Map.of(
            "02", "0441",
            "03", "0442",
            "04", "0443",
            "11", "0444",
            "12", "0445",
            "13", "0446",
            "14", "0447",
            "15", "0448"
    );

    private CardTypeMapping() {
    }

    public static String toIssueCardType(String cardType) {
        if (cardType == null) {
            return null;
        }
        String normalized = cardType.trim();
        if (AI_SHAN_DONG_CARD_TYPE.equalsIgnoreCase(normalized)) {
            return AI_SHAN_DONG_CARD_TYPE;
        }
        return ISSUE_CARD_TYPES.getOrDefault(normalized, normalized);
    }

    public static boolean isSupportedAppCardType(String cardType) {
        if (cardType == null) {
            return false;
        }
        String normalized = cardType.trim();
        return ISSUE_CARD_TYPES.containsKey(normalized)
                || AI_SHAN_DONG_CARD_TYPE.equalsIgnoreCase(normalized);
    }

    public static boolean isAiShanDong(String cardType) {
        return AI_SHAN_DONG_CARD_TYPE.equalsIgnoreCase(cardType == null ? null : cardType.trim());
    }
}
