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

    /** 查询最近一次进站交易记录（trxType=01）。 */
    QRCodeTxnDetail selectLatestEntryByCardId(@Param("cardId") String cardId);

    QRCodeTxnDetail selectFirstEntryBySequence(@Param("cardId") String cardId,
                                               @Param("ticketTransSeq") String ticketTransSeq);

    /**
     * IF8A-05 查询交易记录列表。
     *
     * @deprecated 请迁移至 {@link com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper#selectTransList}
     */
    @Deprecated
    java.util.List<com.chinasofti.huateng.model.app.TransRecordDTO> selectTransList(QueryTransListReqDTO request);

    /**
     * IF8A-05 查询交易记录总数。
     *
     * @deprecated 请迁移至 {@link com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper#countTransList}。
     */
    @Deprecated
    int countTransList(QueryTransListReqDTO request);

    /**
     * IF8A-41 查询账单统计。
     *
     * @deprecated 已迁至 {@code GateTxnPayMapper#selectTransStatistics}（源表 {@code GATE_TXN_PAY}）。
     */
    @Deprecated
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

    /** 运营端展示用：按站点编码批量查询 STATION_INFO 中文站名，行映射键为 STATION_CODE / STATION_NAME。 */
    List<java.util.Map<String, Object>> selectStationNames(@Param("codes") List<String> codes);

    /** 支付宝出行-查询乘车记录列表。 */
    List<AlipayTripTravelRecordDTO> selectAlipayTravelList(@Param("thirdUserId") String thirdUserId,
                                                            @Param("startTime") String startTime,
                                                            @Param("endTime") String endTime,
                                                            @Param("offset") int offset,
                                                            @Param("limit") int limit);

    /** 支付宝出行-查询乘车记录总数。 */
    int countAlipayTravelList(@Param("thirdUserId") String thirdUserId,
                               @Param("startTime") String startTime,
                               @Param("endTime") String endTime);

    /** 支付宝出行-查询乘车记录详情。 */
    AlipayTripTravelRecordDTO selectAlipayTravelDetail(@Param("thirdUserId") String thirdUserId,
                                                       @Param("orderNo") String orderNo,
                                                       @Param("handleDateTime") String handleDateTime,
                                                       @Param("trxType") String trxType,
                                                       @Param("cardId") String cardId);

    /** 支付宝出行-按订单号查询乘车记录详情。 */
    AlipayTripTravelRecordDTO selectAlipayTravelDetailByOrderNo(@Param("orderNo") String orderNo);

    /** 支付宝出行-按用户+时间+交易类型+卡号查询乘车记录详情。 */
    AlipayTripTravelRecordDTO selectAlipayTravelDetailByUserAndDateTime(@Param("thirdUserId") String thirdUserId,
                                                                        @Param("handleDateTime") String handleDateTime,
                                                                        @Param("trxType") String trxType);

    /** IF8A-04 查询补站交易明细（业务幂等校验）。 */
    QRCodeTxnDetail selectExcessFareDetail(@Param("cardId") String cardId,
                                           @Param("trxType") String trxType,
                                           @Param("handleDateTime") String handleDateTime,
                                           @Param("ticketTransSeq") String ticketTransSeq);

    /** IF8A-34 查询订单详情（返回进站+出站等所有记录，由 Service 层合并）。 */
    java.util.List<com.chinasofti.huateng.model.app.TransRecordDTO> selectTransDetail(@Param("thirdUserId") String thirdUserId,
                                                       @Param("orderNo") String orderNo);
}
