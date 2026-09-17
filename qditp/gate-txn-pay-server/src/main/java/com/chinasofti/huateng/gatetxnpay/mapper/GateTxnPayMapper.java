package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.model.page.OfflineCodeStatView;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface GateTxnPayMapper {
    /** 新增过闸扣费订单。 */
    int insert(GateTxnPay record);

    /** 按交易业务键查询订单，用于闸机重复通知幂等处理。 */
    GateTxnPay selectByBizKey(@Param("cardId") String cardId,
                              @Param("trxType") String trxType,
                              @Param("outTime") String outTime,
                              @Param("ticketTransSeq") String ticketTransSeq,
                              @Param("deviceId") String deviceId,
                              @Param("txnDate") String txnDate);

    /** 待支付态推进：仅允许从 INIT / RETRY 更新为 PROCESSING / RETRY。 */
    int updateStatusFromPending(@Param("orderNo") String orderNo,
                                @Param("txnDate") String txnDate,
                                @Param("debitStatus") String debitStatus,
                                @Param("remark") String remark);

    /** 终态收敛：仅允许从 PROCESSING 更新为 SUCCESS / FAIL，防止重复回调。 */
    int updateStatusIfProcessing(@Param("orderNo") String orderNo,
                                 @Param("txnDate") String txnDate,
                                 @Param("debitStatus") String debitStatus,
                                 @Param("remark") String remark);

    /** 支付结果回调驱动的终态收敛：仅允许 INIT / PROCESSING / RETRY 更新为 SUCCESS / FAIL。 */
    int convergeDebitStatus(@Param("orderNo") String orderNo,
                            @Param("txnDate") String txnDate,
                            @Param("debitStatus") String debitStatus,
                            @Param("remark") String remark);

    /**
     * 在线补款支付成功后的收敛：允许 INIT / PROCESSING / RETRY / FAIL 更新为 SUCCESS。
     *
     * @return 影响行数，0 行不等于失败。
     */
    int convergeDebitStatusForSupplement(@Param("orderNo") String orderNo,
                                         @Param("txnDate") String txnDate,
                                         @Param("remark") String remark);

    /** 更新扣费当前状态。 */
    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("txnDate") String txnDate,
                     @Param("debitStatus") String debitStatus,
                     @Param("remark") String remark);

    /** 运营端分页查询扣费订单，参数需与 countOperationPage 保持一致。 */
    List<GateTxnPay> selectOperationPage(@Param("orderNo") String orderNo,
                                         @Param("cardId") String cardId,
                                         @Param("thirdUserId") String thirdUserId,
                                         @Param("signChannelCode") String signChannelCode,
                                         @Param("cardType") String cardType,
                                         @Param("debitStatus") String debitStatus,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 统计运营端扣费订单分页结果总数。 */
    int countOperationPage(@Param("orderNo") String orderNo,
                           @Param("cardId") String cardId,
                           @Param("thirdUserId") String thirdUserId,
                           @Param("signChannelCode") String signChannelCode,
                           @Param("cardType") String cardType,
                           @Param("debitStatus") String debitStatus,
                           @Param("startDate") String startDate,
                           @Param("endDate") String endDate);

    /** 运营端分页展示用：按站点编码批量查询 STATION_INFO 中文站名，行映射键为 STATION_CODE / STATION_NAME。 */
    List<Map<String, Object>> selectStationNames(@Param("codes") List<String> codes);

    /** 按车站分组统计日期窗内离线码（{@code OFFLINE_FLAG='Y'}）交易笔数与独立卡数。 */
    List<OfflineCodeStatView> countOfflineByStationGroup(@Param("startDate") String startDate,
                                                         @Param("endDate") String endDate);

    /** 汇总日期窗内离线码交易总笔数与独立卡数（跨车站去重，因此不能对分组行求和）。 */
    OfflineCodeStatView countOfflineSummary(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate);

    /** 退款前锁定查询指定扣费订单的可退款状态和金额。 */
    GateTxnPay selectByOrderNo(@Param("orderNo") String orderNo);

    /** 圈出日期窗内可退超时罚金的订单分页列表。 */
    List<GateTxnPay> selectOvertimeRefundablePage(@Param("stationCode") String stationCode,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    /** 统计圈单结果总数。 */
    int countOvertimeRefundable(@Param("stationCode") String stationCode,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate);

    /** 按交易业务键查询订单（用于 ticket-server 查询 GT 订单号）。 */
    GateTxnPay selectByBizKeyForQuery(@Param("cardId") String cardId,
                                      @Param("trxType") String trxType,
                                      @Param("outTime") String outTime,
                                      @Param("ticketTransSeq") String ticketTransSeq,
                                      @Param("deviceId") String deviceId,
                                      @Param("txnDate") String txnDate);

    /**
     * 分页查询进出站交易记录（用于 IF8A-05）。
     *
     * @param debitRequestResult 扣款结果过滤：null 或空=全部，"0"=已扣款成功，"1"=未扣款成功。
     * @param cardTypeList 发卡卡类型列表，非空时按 {@code CARD_TYPE IN (...)} 过滤并忽略。
     */
    List<GateTxnPay> selectTransList(@Param("thirdUserId") String thirdUserId,
                                     @Param("cardIdList") List<String> cardIdList,
                                     @Param("cardType") String cardType,
                                     @Param("cardTypeList") List<String> cardTypeList,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate,
                                     @Param("ticketCode") String ticketCode,
                                     @Param("debitRequestResult") String debitRequestResult,
                                     @Param("offset") Integer offset,
                                     @Param("limit") Integer limit);

    /** 统计 IF8A-05 分页查询结果总数。 */
    int countTransList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardIdList") List<String> cardIdList,
                       @Param("cardType") String cardType,
                       @Param("cardTypeList") List<String> cardTypeList,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate,
                       @Param("ticketCode") String ticketCode,
                       @Param("debitRequestResult") String debitRequestResult);

    /**
     * IF8A-41 账单统计：一条 SQL 同时算出原价 / 实付 / 优惠 / 超时费。
     *
     * @return 永不为 null（{@code COUNT(1)} 保证有一行），但 {@code SUM()} 在零行时返回 NULL。
     */
    TripDataDTO selectTransStatistics(@Param("request") RequestTransStatisticsReqDTO request);

    /** 查询用户指定支付渠道在指定时间之后是否存在扣费失败订单。 */
    int countFailedOrder(@Param("thirdUserId") String thirdUserId,
                         @Param("paymentVendor") String paymentVendor,
                         @Param("requestTime") LocalDateTime requestTime);

    /** 查询单张卡是否存在未结清扣费订单（供 blacklist-server 自动解除黑名单调用）。 */
    int countUnsettledOrderByCardId(@Param("cardId") String cardId);

    /** 一条 SQL 同时统计用户的「未支付」与「扣费失败」订单数（IF8A-35）。 */
    RequestUserAccInfoResult countUserAccInfo(@Param("thirdUserId") String thirdUserId,
                                             @Param("startDate") String startDate);

    /** 按订单号列表批量查询扣费订单（用于 IF8A-26 校验待补款的原订单）。 */
    List<GateTxnPay> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    /** 查询指定交易日期区间内 {@code ORIGINAL_FARE} 为空、且进出站齐全的订单。 */
    List<GateTxnPay> selectMissingOriginalFare(@Param("startDate") String startDate,
                                              @Param("endDate") String endDate,
                                              @Param("limit") int limit);

    /** 回填 {@code ORIGINAL_FARE}，仅当该列仍为空时生效。 */
    int updateOriginalFareIfNull(@Param("orderNo") String orderNo,
                                 @Param("txnDate") String txnDate,
                                 @Param("originalFare") Integer originalFare);

    /** 查询离线码出站时金额重算失败、等待补偿的订单。 */
    List<GateTxnPay> selectOfflineFarePending(@Param("startDate") String startDate,
                                             @Param("endDate") String endDate,
                                             @Param("limit") int limit);

    /** 重算成功后回写金额与优惠快照，并把 {@code DISCOUNT_CALC_STATUS} 推出 PENDING 态。 */
    int updateOfflineFareRecalculated(GateTxnPay order);

    /** 重算再次失败时只追加失败原因，保持 PENDING 态等下一轮。 */
    int updateOfflineFarePendingMsg(@Param("orderNo") String orderNo,
                                    @Param("txnDate") String txnDate,
                                    @Param("discountCalcMsg") String discountCalcMsg);
}
