package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.model.cardpool.CardPoolTicketType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * APP 入参卡类型到发卡卡类型的映射。
 */
public final class CardTypeMapping {
    public static final String AI_SHAN_DONG_CARD_TYPE = "044A";

    /**
     * APP「电子日票」页签上送的聚合票种码（用户。
     */
    public static final String DAY_TICKET_BUCKET_CARD_TYPE = "05";

    /**
     * APP「NFC 过闸」页签上送的票种码：旧 NFC {@code 03} 与新 NFC {@code 04}（用户。
     */
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

    /** 日票族发卡卡类型：一日票 / 三日票 / 七日票 / 月票，对应 APP 的聚合码 05。 */
    private static final List<String> DAY_TICKET_ISSUE_TYPES = List.of("0445", "0446", "0447", "0448");

    /** NFC 族发卡卡类型：旧 NFC 卡 / 新 NFC 卡，对应 APP 的 03 与 04。 */
    private static final List<String> NFC_ISSUE_TYPES = List.of("0442", "0443");

    private CardTypeMapping() {
    }

    /**
     * APP 入参卡类型转发卡卡类型（4 位 044X）。
     */
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

    /**
     * APP 卡类型映射为一个或多个发卡卡类型，供 SQL 的 {@code CARD_TYPE IN (...)} 使用。
     */
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

    /**
     * 是否为受支持的入参卡类型。识别 APP 口径映射表的键、日票聚合码 {@code 05}
     */
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
