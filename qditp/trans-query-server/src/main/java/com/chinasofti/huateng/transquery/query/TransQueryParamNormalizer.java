package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.CardTypeMapping;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
class TransQueryParamNormalizer {

    private static final DateTimeFormatter DASHED_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter COMPACT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final int COMPACT_LENGTH = 8;


    String normalizeDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return null;
        }
        String trimmed = dateStr.trim();
        try {
            if (trimmed.length() == COMPACT_LENGTH) {
                LocalDate.parse(trimmed, COMPACT_FORMATTER);
                return trimmed;
            }
            LocalDate.parse(trimmed, DASHED_FORMATTER);
            return trimmed.replace("-", "");
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式非法: " + dateStr + "，期望格式 yyyy-MM-dd 或 yyyyMMdd");
        }
    }

    List<String> parseCardIds(String cardId) {
        if (!StringUtils.hasText(cardId)) {
            return Collections.emptyList();
        }
        return Arrays.stream(cardId.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }


    List<String> expandCardTypes(String appCardType) {
        if (!CardTypeMapping.isSupportedAppCardType(appCardType)) {
            return null;
        }
        return CardTypeMapping.toIssueCardTypes(appCardType);
    }
}
