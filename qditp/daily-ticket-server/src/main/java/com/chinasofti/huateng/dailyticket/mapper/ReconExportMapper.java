package com.chinasofti.huateng.dailyticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 日终对账抽取专用只读 Mapper（来源标识 {@code daily-ticket}，负责 DETAIL 与 PAY 两类文件）。 */
@Mapper
public interface ReconExportMapper {

    /**
     * DETAIL：按 Keyset 游标翻页取「车票购买（发售）」明细，游标为 {@code (PAY_DATE, ORDER_NO)}。
     *
     * @param windowStart 窗口起点（含）
     * @param windowEnd   窗口终点（不含）
     * @param lastPayDate 上一页最后一行的 {@code PAY_DATE}，首页传 null
     * @param lastOrderNo 上一页最后一行的 {@code ORDER_NO}，首页传 null
     * @param limit       本页最大行数
     * @return 明细行，列名见 XML 的 select 列表
     */
    List<Map<String, Object>> selectDetailPage(@Param("windowStart") Timestamp windowStart,
                                               @Param("windowEnd") Timestamp windowEnd,
                                               @Param("lastPayDate") Timestamp lastPayDate,
                                               @Param("lastOrderNo") String lastOrderNo,
                                               @Param("limit") int limit);

    /**
     * PAY：取旅游票发售汇总，按「日期 + 支付方式」在库内 GROUP BY 后返回。
     *
     * <p>口径取 <b>主单 {@code TRAVEL_TICKET_ORDER}</b>：旅游票是「主单聚合支付一次」，
     * 支付终态只回写主单，子单（{@code DAILY_TICKET_ORDER} 里 {@code PARENT_ORDER_NO} 非空那些）
     * 恒为 {@code CREATED}/{@code INIT}、{@code PAY_DATE} 恒空。
     * <b>NEVER 改回按子单统计</b> —— 2026-09-22 实测：改回去这条查询恒返 0 行，
     * 旅游票在 {@code ITP.PAY} 里就一直没有数据（该缺陷自上线起存在，当日修复）。
     * 张数取 {@code SUM(TICKET_COUNT)}（一张主单含 N 张票，NEVER 用 {@code COUNT(*)} —— 那数的是订单数）。
     *
     * @param windowStart 窗口起点（含）
     * @param windowEnd   窗口终点（不含）
     * @return 汇总行，含 {@code TXN_DATE} / {@code PAY_CHANNEL_CODE} / {@code TICKET_COUNT} / {@code TICKET_AMOUNT}
     */
    List<Map<String, Object>> selectTravelTicketPaySummary(@Param("windowStart") Timestamp windowStart,
                                                           @Param("windowEnd") Timestamp windowEnd);
}
