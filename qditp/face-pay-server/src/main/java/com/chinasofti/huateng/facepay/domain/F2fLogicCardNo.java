package com.chinasofti.huateng.facepay.domain;

import java.util.Locale;

/**
 * 票逻辑卡号（{@code ticketLogicNum} / {@code TICKET_LOGIC_NUM}）的大小写归一，全模块唯一实现。
 *
 * <p>这是对 AGENTS.md §5.1「NEVER 新建工具类」的有意破例：归一点有 8 处、散在 3 个 service，
 * 抄私有方法等于 3 份逐字副本，而漏掉任何一处就会重新制造「入库一种大小写、查询另一种」的静默不匹配。
 *
 * <p>为什么必须归一：{@code F2fTicketMapper} 的四条语句全是精确等值（走
 * {@code UK_F2F_TICKET_LOGIC} / {@code IDX_F2F_TICKET_RECENT}），{@code F2F_REFUND} 的幂等
 * 又依赖 {@code NVL(TICKET_LOGIC_NUM, '#WHOLE#')} 函数唯一索引 —— 大小写不一致时查询命中 0 行、
 * 幂等键也对不上，表现为业务码 8999「没有查找到出票信息」而链路本身完全正常。
 * 2026-09-17 实测：设备出票上报送小写 {@code 0026070101003c68}、交易查询送大写
 * {@code 0026070101003C68}，同一台设备两条报文自己不一致。
 *
 * <p><b>NEVER 改成在 mapper 里写 {@code UPPER(TICKET_LOGIC_NUM) = ...}</b> —— 那会让上面两个
 * 索引在这条谓词上失效，除非另建函数索引。归一 MUST 做在入参侧，让列值与查询值都是大写。
 */
public final class F2fLogicCardNo {

    private F2fLogicCardNo() {
    }

    /**
     * 归一为大写。
     *
     * @param logicCardNo 逻辑卡号，允许为 null
     * @return 大写形式；入参为 null 时返回 null（整单退款场景该值就是 null，MUST 保持）
     */
    public static String normalize(String logicCardNo) {
        return logicCardNo == null ? null : logicCardNo.toUpperCase(Locale.ROOT);
    }
}
