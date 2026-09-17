package com.chinasofti.huateng.gatetxnpay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 日终对账抽取专用查询，只读 {@code GATE_TXN_PAY}，不复用任何联机链路的 mapper。 */
@Mapper
public interface ReconExportMapper {

    /**
     * EXP 单边交易明细抽取，keyset（游标）分页。
     *
     * @param startDate 交易日期下限（含），{@code yyyyMMdd}
     * @param endDate 交易日期上限（含），{@code yyyyMMdd}
     * @param windowStart 出站时间下限（含），{@code yyyyMMddHHmmss}
     * @param windowEnd 出站时间上限（不含），{@code yyyyMMddHHmmss}
     * @param lastOutTime 上一批末行的 {@code OUT_TIME}，首批传 null。
     * @param lastId 上一批末行的 {@code ID}，首批传 null。
     * @param limit 本批最大行数。
     * @return 每行含 ID / CARD_ID / TICKET_TRANS_SEQ / TOTAL_AMOUNT / IN_TIME / DEVICE_ID /。
     */
    List<Map<String, Object>> selectExpPage(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("windowStart") String windowStart,
                                            @Param("windowEnd") String windowEnd,
                                            @Param("lastOutTime") String lastOutTime,
                                            @Param("lastId") Long lastId,
                                            @Param("limit") int limit);

    /**
     * PAY 汇总的「过闸」两段度量，在数据库内 GROUP BY，一次查回全部结果。
     *
     * @return 每行含 TXN_DATE / OUT_STATION / DEVICE_ID / SIGN_CHANNEL_CODE / TXN_COUNT / PAY_AMOUNT。
     */
    List<Map<String, Object>> selectPayGateSummary(@Param("startDate") String startDate,
                                                    @Param("endDate") String endDate,
                                                    @Param("windowStart") String windowStart,
                                                    @Param("windowEnd") String windowEnd);

    /**
     * PAY 汇总的「单边交易」两段度量，键与度量结构同 {@link #selectPayGateSummary}，只有 WHERE 不同：这里筛 {@code ORDER_EXP_TYPE} 非空非空格的异常/单边订单。
     *
     * @return 每行含 TXN_DATE / OUT_STATION / DEVICE_ID / SIGN_CHANNEL_CODE / TXN_COUNT / PAY_AMOUNT。
     */
    List<Map<String, Object>> selectPayExpSummary(@Param("startDate") String startDate,
                                                   @Param("endDate") String endDate,
                                                   @Param("windowStart") String windowStart,
                                                   @Param("windowEnd") String windowEnd);

    /**
     * BUS 商业优惠汇总，按 {@code TXN_DATE} 单键 GROUP BY，一次查回。
     *
     * @return 每行含 TXN_DATE / RECON_AMOUNT / PAY_AMOUNT。
     */
    List<Map<String, Object>> selectBusSummary(@Param("startDate") String startDate,
                                                @Param("endDate") String endDate,
                                                @Param("windowStart") String windowStart,
                                                @Param("windowEnd") String windowEnd);

    /**
     * DETAIL 虚拟电子多日计次票明细抽取，keyset（游标）分页，游标与限制同 {@link #selectExpPage}。
     *
     * @return 每行含 ID / TXN_DATE / CARD_ID / OUT_TIME / OVERTIME_AMOUNT / EXIT_STATION_NAME / DEVICE_ID。
     */
    List<Map<String, Object>> selectDetailPage(@Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("windowStart") String windowStart,
                                               @Param("windowEnd") String windowEnd,
                                               @Param("lastOutTime") String lastOutTime,
                                               @Param("lastId") Long lastId,
                                               @Param("limit") int limit);
}
