package com.chinasofti.huateng.model.cardpool;

import java.util.List;
import java.util.Set;

/** Canonical logical-card ticket types and ACC sub-type conversion. */
public final class CardPoolTicketType {
    /**
     * 全部逻辑卡票种，按票种码升序，供需要稳定顺序的展示场景遍历。
     */
    public static final List<String> ORDERED_TYPES =
            List.of("0441", "0442", "0443", "0444", "0445", "0446", "0447", "0448", "044A");

    /**
     * 全部逻辑卡票种集合，由 {@link #ORDERED_TYPES} 去序化而来，供合法性校验用。
     */
    public static final Set<String> ALL_TYPES = Set.copyOf(ORDERED_TYPES);

    /**
     * 走逻辑卡号池发卡的票种子集，不含由安全服务直接发卡的 0442 / 0443。
     */
    public static final Set<String> POOL_ENABLED_TYPES = Set.of("0441", "0444", "0445", "0446", "0447", "0448", "044A");

    /**
     * 由安全服务直接发卡（不进卡池）的票种。
     */
    public static final Set<String> SECURITY_SERVICE_TYPES = Set.of("0442", "0443");

    private CardPoolTicketType() { }

    /**
     * 规范化票种码：去首尾空白并转大写，仅当结果属于 {@link #ALL_TYPES} 时才返回，否则视为非法票种。
     *
     * @param cardType 待校验的 4 位票种码，允许为 {@code null}
     * @return 规范化后的票种码；入参为 {@code null} 或不在受支持票种范围内时返回 {@code null}
     */
    public static String normalize(String cardType) {
        if (cardType == null) {
            return null;
        }
        String normalized = cardType.trim().toUpperCase();
        return ALL_TYPES.contains(normalized) ? normalized : null;
    }

    /**
     * 把 4 位全票种码转换为 ACC 接口所用的 2 位票种码，即取 044X 的后两位。
     *
     * @param fullTicketType 4 位全票种码，如 {@code 0441}
     * @return 2 位 ACC 票种码，如 {@code 41}
     * @throws IllegalArgumentException 入参不是受支持的逻辑卡票种时抛出
     */
    public static String toAccTicketType(String fullTicketType) {
        String normalized = normalize(fullTicketType);
        if (normalized == null) {
            throw new IllegalArgumentException("unsupported logical-card ticket type: " + fullTicketType);
        }
        return normalized.substring(2);
    }

    /**
     * {@link #toAccTicketType(String)} 的逆运算：ACC 2 位票种码补 {@code 04} 前缀还原成 4 位全票种码。
     *
     * <p>存在两套票种编码口径，且**取值空间不重叠**，因此可安全共存：APP 口径全是 {@code 0x} / {@code 1x}
     * （{@code CardTypeMapping.ISSUE_CARD_TYPES} 的键 {@code 02/03/04/11/12/13/14/15}），
     * ACC 口径全是 {@code 4x}（{@code 41/44/45/46/47/48/4A}）。交集为空，不会出现同一入参两种解释。</p>
     *
     * <p>已发生事故：2026-09-10 日票下单 {@code cardType=45}（ACC 口径），
     * {@code CardTypeMapping.toIssueCardType} 因映射表无此键而原样返回 {@code 45}，
     * 一路穿到 card-pool-server 预占，与任何 {@code CARD_TYPE} 分区都不匹配，
     * 卡池回 {@code code=null / msg=null}，激活报「发卡服务暂不可用」，
     * 订单 {@code 0E202609101131510006} 支付后 1.5 小时才在激活环节暴露。</p>
     *
     * @param accTicketType ACC 2 位票种码，如 {@code 45}；允许为 {@code null}
     * @return 4 位全票种码，如 {@code 0445}；入参为 {@code null} 或补前缀后不属于
     *         {@link #ALL_TYPES} 时返回 {@code null}（与 {@link #normalize(String)} 行为一致）
     */
    public static String fromAccTicketType(String accTicketType) {
        if (accTicketType == null) {
            return null;
        }
        return normalize("04" + accTicketType.trim());
    }
}
