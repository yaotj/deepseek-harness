package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.model.cardpool.CardPoolTicketType;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public final class CardTypeMapping {
    public static final String AI_SHAN_DONG_CARD_TYPE = "044A";


    public static final String DAY_TICKET_BUCKET_CARD_TYPE = "05";


    private static final List<String> NFC_BUCKET_CARD_TYPES = List.of("03", "04");

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

    private static final List<String> DAY_TICKET_ISSUE_TYPES = List.of("0445", "0446", "0447", "0448");

    private static final List<String> NFC_ISSUE_TYPES = List.of("0442", "0443");

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
        String mapped = ISSUE_CARD_TYPES.get(normalized);
        if (mapped != null) {
            return mapped;
        }
        String fromAcc = CardPoolTicketType.fromAccTicketType(normalized);
        return fromAcc != null ? fromAcc : normalized;
    }


    public static List<String> toIssueCardTypes(String cardType) {
        if (cardType == null || cardType.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = cardType.trim();
        if (DAY_TICKET_BUCKET_CARD_TYPE.equals(normalized)) {
            return DAY_TICKET_ISSUE_TYPES;
        }
        if (NFC_BUCKET_CARD_TYPES.contains(normalized)) {
            return NFC_ISSUE_TYPES;
        }
        return List.of(toIssueCardType(normalized));
    }


    public static boolean isSupportedAppCardType(String cardType) {
        if (cardType == null) {
            return false;
        }
        String normalized = cardType.trim();
        return ISSUE_CARD_TYPES.containsKey(normalized)
                || DAY_TICKET_BUCKET_CARD_TYPE.equals(normalized)
                || AI_SHAN_DONG_CARD_TYPE.equalsIgnoreCase(normalized)
                || CardPoolTicketType.fromAccTicketType(normalized) != null;
    }

    public static boolean isAiShanDong(String cardType) {
        return AI_SHAN_DONG_CARD_TYPE.equalsIgnoreCase(cardType == null ? null : cardType.trim());
    }
}
