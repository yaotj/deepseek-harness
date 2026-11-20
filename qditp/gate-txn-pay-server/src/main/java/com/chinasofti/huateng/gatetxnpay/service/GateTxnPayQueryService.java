package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.model.app.CardFailedCountRespDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface GateTxnPayQueryService {
    ResultVO<Map<String, Object>> page(String orderNo, String cardId, String thirdUserId, String signChannelCode,
                                        String cardType, String debitStatus, String issueChannelCode,
                                        String startDate, String endDate,
                                        Integer pageNum, Integer pageSize);

    GateTxnPayRespDTO queryOrderByBizKey(GateTxnPayReqDTO request);


    List<GateTxnPayListDTO> selectTransList(@Param("thirdUserId") String thirdUserId,
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

    RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request);

    GateTxnPayListDTO selectByOrderNo(@Param("orderNo") String orderNo);
    GateTxnPayFailedOrderRespDTO hasFailedOrder(@Param("thirdUserId") String thirdUserId,
                                                @Param("paymentVendor") String paymentVendor,
                                                @Param("requestTime") LocalDateTime requestTime);

    CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@Param("cardId") String cardId);

    CardFailedCountRespDTO countFailedOrderByCard(@Param("cardId") String cardId);

    ResultVO<Map<String, Object>> offlineStats(String startDate, String endDate);

    ResultVO<Map<String, Object>> overtimeRefundablePage(String stationCode, String startDate, String endDate,
                                                         Integer pageNum, Integer pageSize);


    ResultVO<Map<String, Object>> oneSidePage(String orderNo, String cardId, String thirdUserId,
                                              String orderExpType, String startDate, String endDate,
                                              Integer pageNum, Integer pageSize);
    RequestUserAccInfoResult requestUserAccInfo(RequestUserAccInfoReqDTO request);
}
