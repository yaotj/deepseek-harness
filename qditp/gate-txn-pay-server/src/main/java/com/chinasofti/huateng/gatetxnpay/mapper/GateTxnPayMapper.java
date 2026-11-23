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
    int insert(GateTxnPay record);

    GateTxnPay selectByBizKey(@Param("cardId") String cardId,
                              @Param("trxType") String trxType,
                              @Param("outTime") String outTime,
                              @Param("ticketTransSeq") String ticketTransSeq,
                              @Param("deviceId") String deviceId,
                              @Param("txnDate") String txnDate);

    int updateStatusFromPending(@Param("orderNo") String orderNo,
                                @Param("txnDate") String txnDate,
                                @Param("debitStatus") String debitStatus,
                                @Param("remark") String remark);

    int updateStatusIfProcessing(@Param("orderNo") String orderNo,
                                 @Param("txnDate") String txnDate,
                                 @Param("debitStatus") String debitStatus,
                                 @Param("remark") String remark);

    int convergeDebitStatus(@Param("orderNo") String orderNo,
                            @Param("txnDate") String txnDate,
                            @Param("debitStatus") String debitStatus,
                            @Param("remark") String remark);


    int convergeDebitStatusForSupplement(@Param("orderNo") String orderNo,
                                         @Param("txnDate") String txnDate,
                                         @Param("remark") String remark);

    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("txnDate") String txnDate,
                     @Param("debitStatus") String debitStatus,
                     @Param("remark") String remark);

    List<GateTxnPay> selectSettledForDivergenceCheck(@Param("txnDateFrom") String txnDateFrom,
                                                     @Param("recentHours") int recentHours,
                                                     @Param("limit") int limit);

    List<GateTxnPay> selectOperationPage(@Param("orderNo") String orderNo,
                                         @Param("cardId") String cardId,
                                         @Param("thirdUserId") String thirdUserId,
                                         @Param("signChannelCode") String signChannelCode,
                                         @Param("cardType") String cardType,
                                         @Param("debitStatus") String debitStatus,
                                         @Param("issueChannelCode") String issueChannelCode,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    int countOperationPage(@Param("orderNo") String orderNo,
                           @Param("cardId") String cardId,
                           @Param("thirdUserId") String thirdUserId,
                           @Param("signChannelCode") String signChannelCode,
                           @Param("cardType") String cardType,
                           @Param("debitStatus") String debitStatus,
                           @Param("issueChannelCode") String issueChannelCode,
                           @Param("startDate") String startDate,
                           @Param("endDate") String endDate);

    List<GateTxnPay> selectOneSidePage(@Param("orderNo") String orderNo,
                                       @Param("cardId") String cardId,
                                       @Param("thirdUserId") String thirdUserId,
                                       @Param("orderExpType") String orderExpType,
                                       @Param("startDate") String startDate,
                                       @Param("endDate") String endDate,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    int countOneSidePage(@Param("orderNo") String orderNo,
                         @Param("cardId") String cardId,
                         @Param("thirdUserId") String thirdUserId,
                         @Param("orderExpType") String orderExpType,
                         @Param("startDate") String startDate,
                         @Param("endDate") String endDate);

    List<Map<String, Object>> selectStationNames(@Param("codes") List<String> codes);

    List<OfflineCodeStatView> countOfflineByStationGroup(@Param("startDate") String startDate,
                                                         @Param("endDate") String endDate);

    OfflineCodeStatView countOfflineSummary(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate);

    GateTxnPay selectByOrderNo(@Param("orderNo") String orderNo);

    List<GateTxnPay> selectOvertimeRefundablePage(@Param("stationCode") String stationCode,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    int countOvertimeRefundable(@Param("stationCode") String stationCode,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate);

    GateTxnPay selectByBizKeyForQuery(@Param("cardId") String cardId,
                                      @Param("trxType") String trxType,
                                      @Param("outTime") String outTime,
                                      @Param("ticketTransSeq") String ticketTransSeq,
                                      @Param("deviceId") String deviceId,
                                      @Param("txnDate") String txnDate);


    List<GateTxnPay> selectTransList(@Param("thirdUserId") String thirdUserId,
                                     @Param("cardIdList") List<String> cardIdList,
                                     @Param("cardType") String cardType,
                                     @Param("cardTypeList") List<String> cardTypeList,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate,
                                     @Param("ticketCode") String ticketCode,
                                     @Param("debitRequestResult") String debitRequestResult,
                                     @Param("issueChannelCode") String issueChannelCode,
                                     @Param("offset") Integer offset,
                                     @Param("limit") Integer limit);

    int countTransList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardIdList") List<String> cardIdList,
                       @Param("cardType") String cardType,
                       @Param("cardTypeList") List<String> cardTypeList,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate,
                       @Param("ticketCode") String ticketCode,
                       @Param("debitRequestResult") String debitRequestResult,
                       @Param("issueChannelCode") String issueChannelCode);


    TripDataDTO selectTransStatistics(@Param("request") RequestTransStatisticsReqDTO request);

    int countFailedOrder(@Param("thirdUserId") String thirdUserId,
                         @Param("paymentVendor") String paymentVendor,
                         @Param("requestTime") LocalDateTime requestTime);

    int countUnsettledOrderByCardId(@Param("cardId") String cardId);

    int countFailedOrderByCardId(@Param("cardId") String cardId);

    RequestUserAccInfoResult countUserAccInfo(@Param("thirdUserId") String thirdUserId,
                                             @Param("startDate") String startDate);

    List<GateTxnPay> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    List<GateTxnPay> selectMissingOriginalFare(@Param("startDate") String startDate,
                                              @Param("endDate") String endDate,
                                              @Param("limit") int limit);

    int updateOriginalFareIfNull(@Param("orderNo") String orderNo,
                                 @Param("txnDate") String txnDate,
                                 @Param("originalFare") Integer originalFare);

    List<GateTxnPay> selectOfflineFarePending(@Param("startDate") String startDate,
                                             @Param("endDate") String endDate,
                                             @Param("limit") int limit);

    int updateOfflineFareRecalculated(GateTxnPay order);

    int updateOfflineFarePendingMsg(@Param("orderNo") String orderNo,
                                    @Param("txnDate") String txnDate,
                                    @Param("discountCalcMsg") String discountCalcMsg);


    List<GateTxnPay> selectBatchRetryCandidates(@Param("alipayChannel") boolean alipayChannel,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("maxTimes") int maxTimes,
                                               @Param("limit") int limit);


    int prepareBatchRetry(@Param("orderNo") String orderNo,
                          @Param("txnDate") String txnDate,
                          @Param("maxTimes") int maxTimes,
                          @Param("backoffMinutes") int backoffMinutes,
                          @Param("failMsg") String failMsg);


    /**
     * 卡在 INIT 的过闸单（2026-11-20 起只捞 INIT，RETRY/FAIL 归 220/255 的日跑任务）。
     *
     * @param minAgeSeconds 落库至少多少秒才允许补发；刚落库的单可能正在走扣费 RPC，立刻再发一笔就是重复扣款
     */
    List<GateTxnPay> selectRecentUnpaidCandidates(@Param("minAgeSeconds") int minAgeSeconds,
                                                 @Param("limit") int limit);


    int prepareRecentRetry(@Param("orderNo") String orderNo,
                           @Param("txnDate") String txnDate,
                           @Param("failMsg") String failMsg);

    int updateDebitFailInfo(@Param("orderNo") String orderNo,
                            @Param("txnDate") String txnDate,
                            @Param("failCode") String failCode,
                            @Param("failMsg") String failMsg);


    List<GateTxnPay> selectUserRetryCandidates(@Param("thirdUserId") String thirdUserId,
                                              @Param("cardIdList") List<String> cardIdList,
                                              @Param("limit") int limit);


    int prepareUserRetry(@Param("orderNo") String orderNo,
                         @Param("txnDate") String txnDate,
                         @Param("failMsg") String failMsg);


    List<GateTxnPay> selectOneSideInCandidates(@Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("limit") int limit);


    List<GateTxnPay> selectOneSideOutCandidates(@Param("startDate") String startDate,
                                                @Param("endDate") String endDate,
                                                @Param("limit") int limit);
}
