package com.chinasofti.huateng.ticket.recon;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 日终对账抽取专用查询，只读，来源标识 {@code ticket}。 */
@Mapper
public interface ReconExportMapper {

    /**
     * EXP 明细分页查询，keyset 游标分页。
     *
     * @param startDate 窗口起点的 {@code yyyyMMdd}，分区裁剪下限（闭）
     * @param endDate 窗口终点的 {@code yyyyMMdd}，分区裁剪上限（闭）
     * @param windowStart 窗口起点 {@code yyyyMMddHHmmss}（闭）
     * @param windowEnd 窗口终点 {@code yyyyMMddHHmmss}（开）
     * @param lastHandleTime 上一批最后一行的 {@code HANDLE_DATE_TIME}，首批传 null
     * @param lastId 上一批最后一行的 {@code ID}，首批传 null
     * @param limit 本批最大行数
     * @return 明细行，列名见 mapper XML 的 select 列表
     */
    List<Map<String, Object>> selectExpPage(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("windowStart") String windowStart,
                                            @Param("windowEnd") String windowEnd,
                                            @Param("lastHandleTime") String lastHandleTime,
                                            @Param("lastId") Long lastId,
                                            @Param("limit") int limit);

    /**
     * BUS 业务汇总查询，聚合在数据库内完成。
     *
     * @param startDate 窗口起点的 {@code yyyyMMdd}，分区裁剪下限（闭）
     * @param endDate 窗口终点的 {@code yyyyMMdd}，分区裁剪上限（闭）
     * @param windowStart 窗口起点 {@code yyyyMMddHHmmss}（闭）
     * @param windowEnd 窗口终点 {@code yyyyMMddHHmmss}（开）
     * @return 汇总行，列名见 mapper XML 的 select 列表
     */
    List<Map<String, Object>> selectBusSummary(@Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("windowStart") String windowStart,
                                               @Param("windowEnd") String windowEnd);
}
