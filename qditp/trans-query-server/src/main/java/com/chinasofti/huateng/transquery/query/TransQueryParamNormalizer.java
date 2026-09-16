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

/**
 * {@code query} 包内共享的入参归一化 —— 卡号串拆分、日期格式转换、卡类型桶展开。
 *
 * <p><b>这不是通用工具类</b>（AGENTS.md §5.1「NEVER 主动创建新的工具类」）：它只服务
 * {@code query} 包内的三个查询入口，因此**包级可见、NEVER 提升为 public，NEVER 搬到
 * {@code model} / {@code rpc} / {@code micro}**。抽出来的唯一理由是 IF8A-05 与 IF8A-41
 * 共享 {@link #parseCardIds} 与 {@link #expandCardTypes}，而这两处的口径**必须完全一致** ——
 * 一旦分家，「日票聚合码 05 展开成 0445~0448」这条规则改一处漏一处，两个页签的金额就会对不上。
 *
 * <p><b>日期格式两个接口不同，NEVER 互换</b>：IF8A-05 上送 {@code yyyy-MM-dd}（走
 * {@link #normalizeDate} 转成 {@code yyyyMMdd}），IF8A-41 上送的**本来就是 {@code yyyyMMdd}**
 * （与 {@code TXN_DATE} 同格式、直接可比），拿 {@link #normalizeDate} 去解析必抛异常。
 */
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

    /**
     * 解析逗号分隔的多卡号。
     */
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
     * <p><b>MUST 一律映射成列表</b>：APP 的日票聚合码 {@code 05} 会展开成 {@code 0445~0448} 四个发卡
     * 卡类型，单值映射覆盖不全（只能命中一日票）。<b>调用方 MUST 在写入 {@code cardTypeList} 后
     * 把 {@code cardType} 置 null</b>，否则下游会同时拼 {@code CARD_TYPE = ?} 与
     * {@code CARD_TYPE IN (...)} 两个条件而恒命中 0 行。
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
