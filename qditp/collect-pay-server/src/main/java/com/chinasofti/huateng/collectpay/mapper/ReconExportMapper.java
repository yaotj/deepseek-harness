package com.chinasofti.huateng.collectpay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 日终对账抽取专用查询，只读四张收款主表，不复用任何联机链路的 mapper。 */
@Mapper
public interface ReconExportMapper {

    /**
     * ITP.PAY —「BOM/TVM 发售」组之一：TVM 扫码购票（{@code TBL_TVM_ORDER_PAY}），库内 GROUP BY。
     *
     * @param windowStart 窗口下限（含），{@code yyyy-MM-dd HH:mm:ss}
     * @param windowEnd   窗口上限（不含），{@code yyyy-MM-dd HH:mm:ss}
     * @return 每行含 TXN_DATE / LINE_CODE / IN_STATION_CODE / DEVICE_ID / CHANNEL / TXN_COUNT / TXN_AMOUNT
     */
    List<Map<String, Object>> selectTvmPayPaySummary(@Param("windowStart") String windowStart,
                                                     @Param("windowEnd") String windowEnd);

    /**
     * ITP.PAY —「BOM/TVM 充值」组：TVM 扫码充值（{@code TBL_TVM_ORDER_TOPUP}），库内 GROUP BY。
     *
     * @return 每行含 TXN_DATE / DEVICE_ID / CHANNEL / TXN_COUNT / TXN_AMOUNT
     */
    List<Map<String, Object>> selectTvmTopupPaySummary(@Param("windowStart") String windowStart,
                                                       @Param("windowEnd") String windowEnd);

    /**
     * ITP.PAY —「APP 购票」组：APP 在线购票（{@code TBL_TVM_APP_ORDER}），库内 GROUP BY。
     *
     * @return 每行含 TXN_DATE / LINE_CODE / IN_STATION_CODE / PAY_CHANNEL_CODE / TXN_COUNT / TXN_AMOUNT
     */
    List<Map<String, Object>> selectAppOrderPaySummary(@Param("windowStart") String windowStart,
                                                       @Param("windowEnd") String windowEnd);

    /**
     * ITP.PAY —「BOM/TVM 发售」组之二：BOM 非现金收款（{@code TBL_BOM_ORDER_PAY}），库内 GROUP BY。
     *
     * @return 每行含 TXN_DATE / DEVICE_ID / CHANNEL / TXN_COUNT / TXN_AMOUNT
     */
    List<Map<String, Object>> selectBomPayPaySummary(@Param("windowStart") String windowStart,
                                                     @Param("windowEnd") String windowEnd);

    /**
     * ITP.BUS —— TVM 扫码购票按日汇总（{@code TBL_TVM_ORDER_PAY}）。
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectTvmPayBusSummary(@Param("windowStart") String windowStart,
                                                     @Param("windowEnd") String windowEnd);

    /**
     * ITP.BUS —— TVM 扫码充值按日汇总（{@code TBL_TVM_ORDER_TOPUP}）。
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectTvmTopupBusSummary(@Param("windowStart") String windowStart,
                                                       @Param("windowEnd") String windowEnd);

    /**
     * ITP.BUS —— APP 在线购票按日汇总（{@code TBL_TVM_APP_ORDER}），状态列是 {@code PAY_STATUS}。
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectAppOrderBusSummary(@Param("windowStart") String windowStart,
                                                       @Param("windowEnd") String windowEnd);

    /**
     * ITP.BUS —— BOM 非现金收款按日汇总（{@code TBL_BOM_ORDER_PAY}），金额列 {@code TRANS_AOUNT}。
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectBomPayBusSummary(@Param("windowStart") String windowStart,
                                                     @Param("windowEnd") String windowEnd);
}
