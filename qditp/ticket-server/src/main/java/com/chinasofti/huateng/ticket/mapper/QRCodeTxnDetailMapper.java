package com.chinasofti.huateng.ticket.mapper;

import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelRecordDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

    @Mapper
public interface QRCodeTxnDetailMapper {
    int insert(QRCodeTxnDetail record);

    QRCodeTxnDetail selectLatestByCardId(@Param("cardId") String cardId);

    QRCodeTxnDetail selectLatestEntryByCardId(@Param("cardId") String cardId);

    QRCodeTxnDetail selectFirstEntryBySequence(@Param("cardId") String cardId,
                                               @Param("ticketTransSeq") String ticketTransSeq);


    QRCodeTxnDetail selectLatestEntryBeforeExit(@Param("cardId") String cardId,
                                                @Param("exitHandleDateTime") String exitHandleDateTime);


    @Deprecated
    java.util.List<com.chinasofti.huateng.model.app.TransRecordDTO> selectTransList(QueryTransListReqDTO request);


    @Deprecated
    int countTransList(QueryTransListReqDTO request);


    @Deprecated
    RequestTransStatisticsResult selectTransStatistics(@Param("request") RequestTransStatisticsReqDTO request);

    List<QRCodeTxnDetail> selectOperationPage(@Param("cardId") String cardId,
                                               @Param("thirdUserId") String thirdUserId,
                                               @Param("signChannelCode") String signChannelCode,
                                               @Param("cardType") String cardType,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    int countOperationPage(@Param("cardId") String cardId,
                            @Param("thirdUserId") String thirdUserId,
                            @Param("signChannelCode") String signChannelCode,
                            @Param("cardType") String cardType,
                            @Param("startDate") String startDate,
                            @Param("endDate") String endDate);

    List<java.util.Map<String, Object>> selectStationNames(@Param("codes") List<String> codes);

    List<AlipayTripTravelRecordDTO> selectAlipayTravelList(@Param("thirdUserId") String thirdUserId,
                                                            @Param("startTime") String startTime,
                                                            @Param("endTime") String endTime,
                                                            @Param("offset") int offset,
                                                            @Param("limit") int limit);

    int countAlipayTravelList(@Param("thirdUserId") String thirdUserId,
                               @Param("startTime") String startTime,
                               @Param("endTime") String endTime);

    AlipayTripTravelRecordDTO selectAlipayTravelDetail(@Param("thirdUserId") String thirdUserId,
                                                       @Param("orderNo") String orderNo,
                                                       @Param("handleDateTime") String handleDateTime,
                                                       @Param("trxType") String trxType,
                                                       @Param("cardId") String cardId);

    AlipayTripTravelRecordDTO selectAlipayTravelDetailByOrderNo(@Param("orderNo") String orderNo);

    AlipayTripTravelRecordDTO selectAlipayTravelDetailByUserAndDateTime(@Param("thirdUserId") String thirdUserId,
                                                                        @Param("handleDateTime") String handleDateTime,
                                                                        @Param("trxType") String trxType);

    QRCodeTxnDetail selectExcessFareDetail(@Param("cardId") String cardId,
                                           @Param("trxType") String trxType,
                                           @Param("handleDateTime") String handleDateTime,
                                           @Param("ticketTransSeq") String ticketTransSeq);

    java.util.List<com.chinasofti.huateng.model.app.TransRecordDTO> selectTransDetail(@Param("thirdUserId") String thirdUserId,
                                                       @Param("orderNo") String orderNo);
}
