package com.chinasofti.huateng.model.cardpool;

import java.util.List;
import java.util.Set;

public final class CardPoolTicketType {

    public static final List<String> ORDERED_TYPES =
            List.of("0441", "0442", "0443", "0444", "0445", "0446", "0447", "0448", "044A");


    public static final Set<String> ALL_TYPES = Set.copyOf(ORDERED_TYPES);


    public static final Set<String> POOL_ENABLED_TYPES = Set.of("0441", "0444", "0445", "0446", "0447", "0448", "044A");


    public static final Set<String> SECURITY_SERVICE_TYPES = Set.of("0442", "0443");

    private CardPoolTicketType() { }


    public static String normalize(String cardType) {
        if (cardType == null) {
            return null;
        }
        String normalized = cardType.trim().toUpperCase();
        return ALL_TYPES.contains(normalized) ? normalized : null;
    }


    public static String toAccTicketType(String fullTicketType) {
        String normalized = normalize(fullTicketType);
        if (normalized == null) {
            throw new IllegalArgumentException("unsupported logical-card ticket type: " + fullTicketType);
        }
        return normalized.substring(2);
    }


    public static String fromAccTicketType(String accTicketType) {
        if (accTicketType == null) {
            return null;
        }
        return normalize("04" + accTicketType.trim());
    }
}
