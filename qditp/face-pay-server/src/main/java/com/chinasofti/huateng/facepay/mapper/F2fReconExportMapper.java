package com.chinasofti.huateng.facepay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 日终对账抽取专用查询，只读 {@code F2F_ORDER} 与 {@code F2F_PAYMENT}，不复用任何联机链路的 mapper。 */
@Mapper
public interface F2fReconExportMapper {

    /**
     * ITP.PAY —「BOM/TVM 发售」组（段 5 / 6）：设备侧购票，{@code BIZ_TYPE='01'} 且 {@code CHANNEL IN ('02','03')}（02-TVM，03-BOM）。
     *
     * @param windowStart 窗口下限（含）
     * @param windowEnd   窗口上限（不含）
     * @return 每行含 TXN_DATE / LINE_CODE / STATION_CODE / DEVICE_ID / PAY_CHANNEL_CODE
     */
    List<Map<String, Object>> selectDeviceSalePaySummary(@Param("windowStart") Timestamp windowStart,
                                                        @Param("windowEnd") Timestamp windowEnd);

    /** ITP.PAY —「APP 购票」组（段 13 / 14）：{@code BIZ_TYPE='01'} 且 {@code CHANNEL='01'}。 */
    List<Map<String, Object>> selectAppSalePaySummary(@Param("windowStart") Timestamp windowStart,
                                                     @Param("windowEnd") Timestamp windowEnd);

    /** ITP.PAY —「BOM/TVM 充值」组（段 7 / 8）：{@code BIZ_TYPE='02'}，不分渠道。 */
    List<Map<String, Object>> selectTopupPaySummary(@Param("windowStart") Timestamp windowStart,
                                                   @Param("windowEnd") Timestamp windowEnd);

    /** ITP.PAY —「BOM 行政处理」组（段 15 / 16）：{@code BIZ_TYPE='04'} 且 {@code TRANS_TYPE='42'}。 */
    List<Map<String, Object>> selectBomAdminPaySummary(@Param("windowStart") Timestamp windowStart,
                                                      @Param("windowEnd") Timestamp windowEnd);

    /** ITP.PAY —「BOM 处理」组（段 19 / 20）：{@code BIZ_TYPE='04'} 且 {@code TRANS_TYPE} 不是 {@code '42'}（含 NULL），即 {@code 02 超时更新 / 03 超程更新 / 04 未出站 / 05 无入站 / 06 退卡退票 / 22 充值 / 2A 黑名单锁定 / 2B 锁定解除} 这些非行政处理的 BOM 非现金收款。 */
    List<Map<String, Object>> selectBomOtherPaySummary(@Param("windowStart") Timestamp windowStart,
                                                      @Param("windowEnd") Timestamp windowEnd);

    /**
     * ITP.BUS —— 按日汇总，键只有日期一段。
     *
     * @return 每行含 TXN_DATE / TXN_AMOUNT
     */
    List<Map<String, Object>> selectBusSummary(@Param("windowStart") Timestamp windowStart,
                                               @Param("windowEnd") Timestamp windowEnd);

    /** 漏账探针一：窗口内有成功支付、但 {@code BIZ_TYPE} 不在 {@code ('01','02','04')} 的单数。 */
    Long countUncoveredPaidOrders(@Param("windowStart") Timestamp windowStart,
                                  @Param("windowEnd") Timestamp windowEnd);

    /** 漏账探针二：窗口内支付成功（按 {@code F2F_PAYMENT.FINISH_TMS} 落窗）、但订单 {@code PAID_TMS} 为空的单数。 */
    Long countPaidWithoutPaidTms(@Param("windowStart") Timestamp windowStart,
                                 @Param("windowEnd") Timestamp windowEnd);
}
