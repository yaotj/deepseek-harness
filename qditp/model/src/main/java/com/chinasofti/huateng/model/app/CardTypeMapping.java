package com.chinasofti.huateng.model.app;

import java.util.Map;

/**
 * APP 入参卡类型到发卡卡类型的映射。
 */
public final class CardTypeMapping {
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
        return ISSUE_CARD_TYPES.getOrDefault(normalized, normalized);
    }

    public static boolean isSupportedAppCardType(String cardType) {
        return cardType != null && ISSUE_CARD_TYPES.containsKey(cardType.trim());
    }
}
