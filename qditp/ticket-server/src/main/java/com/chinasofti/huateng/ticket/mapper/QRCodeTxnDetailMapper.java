package com.chinasofti.huateng.ticket.mapper;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelRecordDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.ticket.model.app.TransRecordDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

    @Mapper
public interface QRCodeTxnDetailMapper {
    int insert(QRCodeTxnDetail record);

    QRCodeTxnDetail selectLatestByCardId(@Param("cardId") String cardId);

    /**
     * 查询最近一次进站交易记录（trxType=01）。
     */
    QRCodeTxnDetail selectLatestEntryByCardId(@Param("cardId") String cardId);

    /**
     * IF8A-05 查询交易记录列表。
     */
    List<TransRecordDTO> selectTransList(RequestTransListReqDTO request);

    /**
     * IF8A-05 查询交易记录总数。
     */
    int countTransList(RequestTransListReqDTO request);

    /**
     * IF8A-41 查询账单统计。
     */
    RequestTransStatisticsResult selectTransStatistics(@Param("request") RequestTransStatisticsReqDTO request);

    /** 运营端分页查询二维码票卡交易明细，offset/limit 由控制器计算。 */
    List<QRCodeTxnDetail> selectOperationPage(@Param("cardId") String cardId,
                                               @Param("thirdUserId") String thirdUserId,
                                               @Param("signChannelCode") String signChannelCode,
                                               @Param("cardType") String cardType,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    /** 与运营分页查询使用相同筛选条件的总数统计。 */
    int countOperationPage(@Param("cardId") String cardId,
                            @Param("thirdUserId") String thirdUserId,
                            @Param("signChannelCode") String signChannelCode,
                            @Param("cardType") String cardType,
                            @Param("startDate") String startDate,
                            @Param("endDate") String endDate);

    /** 支付宝出行-查询乘车记录列表。 */
     List<AlipayTripTravelRecordDTO> selectAlipayTravelList(@Param("thirdUserId") String thirdUserId,
                                                             @Param("startDate") String startDate,
                                                             @Param("endDate") String endDate,
                                                             @Param("debitRequestResult") String debitRequestResult,
                                                             @Param("offset") int offset,
                                                             @Param("limit") int limit);

     /**
      * 支付宝出行-查询乘车记录总数。
      */
     int countAlipayTravelList(@Param("thirdUserId") String thirdUserId,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate,
                                @Param("debitRequestResult") String debitRequestResult);

    /**
     * 支付宝出行-查询乘车记录详情。
     */
    AlipayTripTravelRecordDTO selectAlipayTravelDetail(@Param("thirdUserId") String thirdUserId,
                                                       @Param("orderNo") String orderNo,
                                                       @Param("handleDateTime") String handleDateTime,
                                                       @Param("trxType") String trxType,
                                                       @Param("cardId") String cardId);

    /**
     * 支付宝出行-按订单号查询乘车记录详情。
     */
    AlipayTripTravelRecordDTO selectAlipayTravelDetailByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 支付宝出行-按用户+时间+交易类型+卡号查询乘车记录详情。
     */
    AlipayTripTravelRecordDTO selectAlipayTravelDetailByUserAndDateTime(@Param("thirdUserId") String thirdUserId,
                                                                        @Param("handleDateTime") String handleDateTime,
                                                                        @Param("trxType") String trxType);

    /**
     * IF8A-04 查询补站交易明细（业务幂等校验）。
     */
    QRCodeTxnDetail selectExcessFareDetail(@Param("cardId") String cardId,
                                           @Param("trxType") String trxType,
                                           @Param("handleDateTime") String handleDateTime,
                                           @Param("ticketTransSeq") String ticketTransSeq);
}
