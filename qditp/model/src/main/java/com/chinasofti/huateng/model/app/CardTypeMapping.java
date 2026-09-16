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
     * APP「电子日票」页签上送的聚合票种码（用户 2026-09-10 确认语义为「日票聚合桶」）。
     *
     * <p>与 {@link #ISSUE_CARD_TYPES} 里按天数细分的 12~15 不是同一层概念：APP 只有一个
     * 「电子日票」入口，不区分一日 / 三日 / 七日 / 月票，因此 05 必须展开成多个发卡卡类型，
     * 用 {@link #toIssueCardTypes(String)} 取列表，**NEVER** 走单值映射。</p>
     */
    public static final String DAY_TICKET_BUCKET_CARD_TYPE = "05";

    /**
     * APP「NFC 过闸」页签上送的票种码：旧 NFC {@code 03} 与新 NFC {@code 04}（用户 2026-09-10 裁决）。
     *
     * <p>两者在查询侧**同义**：APP 只有一个 NFC 入口，页签实测发 {@code 03}
     * （`/ci/app/requestTransList` 抓包：{@code cardType=03} + {@code cardId=0426091000000013}），
     * 而同一张卡开户时 APP 送的是 {@code 04}（`USER_ITP_REG_INFO.ITP_CARD_TYPE=04`、
     * {@code CARD_TYPE=0443}），闸机上报与订单落库也都是 {@code 0443}。
     * 于是「按 03 查」映射成单值 {@code 0442} 后恒命中 0 行且 {@code retCode=0000} 不报错
     * ——与修复前的日票聚合码 {@code 05} 是同一类缺陷（2026-09-10 定位，订单
     * {@code GT20260910164625246000013} 查不到）。</p>
     *
     * <p>因此 {@code 03} / {@code 04} 一律按「NFC 聚合桶」展开为 {@code 0442}+{@code 0443}，
     * **NEVER 退回单值映射**：APP 传哪个都要能查到两类 NFC 卡的记录。
     * 仅作用于查询链路（{@link #toIssueCardTypes(String)} 只被 IF8A-05 / IF8A-41 调用），
     * 开户侧走 {@link #toIssueCardType(String)} 的单值语义、不受影响。</p>
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

    /** NFC 族发卡卡类型：旧 NFC 卡 / 新 NFC 卡，对应 APP 的 03 与 04，见 {@link #NFC_BUCKET_CARD_TYPES}。 */
    private static final List<String> NFC_ISSUE_TYPES = List.of("0442", "0443");

    private CardTypeMapping() {
    }

    /**
     * APP 入参卡类型转发卡卡类型（4 位 044X）。
     *
     * <p>识别顺序：爱山东 {@code 044A} → APP 口径映射表 {@link #ISSUE_CARD_TYPES}
     * → **ACC 2 位票种码**（补 {@code 04} 前缀，如 {@code 45 → 0445}、{@code 48 → 0448}）
     * → 未识别则原样返回。</p>
     *
     * <p>接受 ACC 口径是因为上游存在两套票种编码且**取值空间不重叠**：APP 口径全是
     * {@code 0x} / {@code 1x}（本类映射表的键），ACC 口径全是 {@code 4x}
     * （{@code CardPoolTicketType.toAccTicketType} 的产物）。交集为空，不存在歧义。
     * 已发生事故见 {@link CardPoolTicketType#fromAccTicketType(String)} 的注释。</p>
     *
     * <p><b>本方法对未识别值原样返回、不抛异常</b>，脏值会静默流到下游（SQL 命中 0 行、
     * 卡池预占匹配不到分区）。因此接口入口 **MUST** 先用
     * {@link #isSupportedAppCardType(String)} 校验，**NEVER** 依赖本方法拦截非法票种。</p>
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
     *
     * <p>与 {@link #toIssueCardType(String)} 的区别在两个聚合桶：
     * <ul>
     *   <li>{@code 05} 日票聚合码 → {@code 0445}~{@code 0448}（见 {@link #DAY_TICKET_BUCKET_CARD_TYPE}）</li>
     *   <li>{@code 03} / {@code 04} NFC → {@code 0442}+{@code 0443}（见 {@link #NFC_BUCKET_CARD_TYPES}）</li>
     * </ul>
     * 其余取值返回单元素列表。</p>
     *
     * <p>入参为空返回空列表（调用方据此不拼卡类型条件，即查全部票种）。未识别的取值
     * 原样返回单元素列表，与 {@link #toIssueCardType(String)} 行为一致——这会让 SQL
     * 命中 0 行且不报错，因此入口 **MUST** 先用 {@link #isSupportedAppCardType(String)} 校验。</p>
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
     * 是否为受支持的入参卡类型。识别 APP 口径映射表的键、日票聚合码 {@code 05}、
     * 爱山东 {@code 044A}，以及 ACC 2 位票种码（{@code 41/44/45/46/47/48/4A}）。
     *
     * <p>接口入口 **MUST** 先调本方法拦非法票种，**NEVER** 依赖
     * {@link #toIssueCardType(String)} 兜底——它对未识别值原样返回、不报错。</p>
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
