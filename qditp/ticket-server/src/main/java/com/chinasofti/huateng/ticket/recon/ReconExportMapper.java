package com.chinasofti.huateng.ticket.recon;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 日终对账抽取专用查询，只读，来源标识 {@code ticket}。
 *
 * <p>本接口只服务 {@code ReconExportService}，与联机链路的
 * {@link QRCodeTxnDetailMapper} 完全隔离：对账口径（导全部 {@code TRX_TYPE}、金额取
 * {@code TRX_AMOUNT + OVERTIME_AMOUNT}）和联机查询口径不同，混在同一个 mapper 里改一处
 * 会连带影响另一条链路。</p>
 *
 * <p>返回类型刻意用 {@code Map} 而非实体：抽取只是把列值拼成管道分隔文本，中间不需要 DTO，
 * 加一层实体反而要为「明细列 + JOIN 出来的 LINE_CODE + 聚合度量」三种形态各造一个类。
 * 注意 Oracle 的 NUMBER 列在 {@code resultType=java.util.Map} 下一律是 {@code BigDecimal}。</p>
 */
@Mapper
public interface ReconExportMapper {

    /**
     * EXP 明细分页查询，keyset 游标分页。
     *
     * <p>游标是 {@code (HANDLE_DATE_TIME, ID)} 二元组，与 {@code ORDER BY} 严格对应：
     * 同一秒内可能有多笔交易，只比时间会漏行或死循环，因此第二段用 ID 破平。
     * <b>NEVER 换成大页码 OFFSET</b>——Oracle 需先产出并丢弃前 n 行，深页是平方级代价。</p>
     *
     * @param startDate      窗口起点的 {@code yyyyMMdd}，分区裁剪下限（闭）
     * @param endDate        窗口终点的 {@code yyyyMMdd}，分区裁剪上限（闭）
     * @param windowStart    窗口起点 {@code yyyyMMddHHmmss}（闭）
     * @param windowEnd      窗口终点 {@code yyyyMMddHHmmss}（开）
     * @param lastHandleTime 上一批最后一行的 {@code HANDLE_DATE_TIME}，首批传 null
     * @param lastId         上一批最后一行的 {@code ID}，首批传 null
     * @param limit          本批最大行数
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
     * <p>按 日期 / 线路 / 车站 / 设备 / 卡类型 五段键 GROUP BY，度量为笔数与金额合计。
     * 分组数只有几百到几千行，一次查回即可，不需要分页。<b>NEVER 改成把明细拉到 Java 侧再
     * 聚合</b>——那等于把几百万行搬进堆内存换几千行结果。</p>
     *
     * @param startDate   窗口起点的 {@code yyyyMMdd}，分区裁剪下限（闭）
     * @param endDate     窗口终点的 {@code yyyyMMdd}，分区裁剪上限（闭）
     * @param windowStart 窗口起点 {@code yyyyMMddHHmmss}（闭）
     * @param windowEnd   窗口终点 {@code yyyyMMddHHmmss}（开）
     * @return 汇总行，列名见 mapper XML 的 select 列表
     */
    List<Map<String, Object>> selectBusSummary(@Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("windowStart") String windowStart,
                                               @Param("windowEnd") String windowEnd);
}
