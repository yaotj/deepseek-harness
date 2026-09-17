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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 日期格式转换：{@code yyyy-MM-dd} -> {@code yyyyMMdd}，顺带校验合法性。
     *
     * @throws IllegalArgumentException 格式非法，由调用方转成 {@code 8001}
     */
    String normalizeDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return null;
        }
        try {
            LocalDate.parse(dateStr, DATE_FORMATTER);
            return dateStr.replace("-", "");
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式非法: " + dateStr + "，期望格式 yyyy-MM-dd");
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
