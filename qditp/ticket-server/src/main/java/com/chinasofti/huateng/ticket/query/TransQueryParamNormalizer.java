package com.chinasofti.huateng.ticket.query;

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

/** {@code query} 包内共享的入参归一化 —— 卡号串拆分、日期格式转换、卡类型桶展开。 */
@Component
class TransQueryParamNormalizer {

    private static final DateTimeFormatter DASHED_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter COMPACT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 紧凑格式的长度，用它区分两种入参形态。 */
    private static final int COMPACT_LENGTH = 8;

    /**
     * 日期归一成下游要的 8 位 {@code yyyyMMdd}，顺带校验合法性。
     *
     * <p><b>{@code yyyy-MM-dd} 与 {@code yyyyMMdd} 两种入参都接受</b>（2026-09-22 业主裁决，与
     * {@code trans-query-server} 里的同名类逐条一致）。此前只认带横线那种，而 APP 对 IF8A-41 送的恰是
     * 8 位，于是那条链路 100% 返 {@code 8001}。<b>NEVER 改回「8 位即拒」</b>；也 NEVER 把 8 位直接原样
     * 返回而跳过 {@link LocalDate#parse}——那样非日期字符串会一路进 SQL。
     *
     * @throws IllegalArgumentException 格式非法，由调用方转成 {@code 8001}
     */
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

    /** 解析逗号分隔的多卡号。 */
    List<String> parseCardIds(String cardId) {
        if (!StringUtils.hasText(cardId)) {
            return Collections.emptyList();
        }
        return Arrays.stream(cardId.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * APP 口径卡类型 → ACC 发卡票种列表。
     *
     * @return null 表示卡类型非法，调用方 MUST 返 {@code 8001}
     */
    List<String> expandCardTypes(String appCardType) {
        if (!CardTypeMapping.isSupportedAppCardType(appCardType)) {
            return null;
        }
        return CardTypeMapping.toIssueCardTypes(appCardType);
    }
}
