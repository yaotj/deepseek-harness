package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GateTxnPayMapper {
    /**
     * 新增过闸扣费订单。
     */
    int insert(GateTxnPay record);

    /**
     * 按交易业务键查询订单，用于闸机重复通知幂等处理。
     */
    GateTxnPay selectByBizKey(@Param("cardId") String cardId,
                              @Param("trxType") String trxType,
                              @Param("outTime") String outTime,
                              @Param("ticketTransSeq") String ticketTransSeq,
                              @Param("deviceId") String deviceId,
                              @Param("txnDate") String txnDate);

    /**
     * 更新扣费当前状态（仅允许从 PROCESSING 更新，防止重复回调）。
     */
    int updateStatusIfProcessing(@Param("orderNo") String orderNo,
                                 @Param("txnDate") String txnDate,
                                 @Param("debitStatus") String debitStatus,
                                 @Param("remark") String remark);

    /**
     * 更新扣费当前状态。
     */
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

    /** 退款前锁定查询指定扣费订单的可退款状态和金额。 */
    GateTxnPay selectByOrderNo(@Param("orderNo") String orderNo);

    /** 按交易业务键查询订单（用于 ticket-server 查询 GT 订单号）。 */
    GateTxnPay selectByBizKeyForQuery(@Param("cardId") String cardId,
                                      @Param("trxType") String trxType,
                                      @Param("outTime") String outTime,
                                      @Param("ticketTransSeq") String ticketTransSeq,
                                      @Param("deviceId") String deviceId,
                                      @Param("txnDate") String txnDate);

    // ==================== IF8A-05 APP 交易记录列表 ====================

    /**
     * 分页查询进出站交易记录（用于 IF8A-05）。
     *
     * <p>返回结果按 OUT_TIME DESC, ID DESC 排序。offset/limit 为 null 时不分页。</p>
     */
    List<GateTxnPay> selectTransList(@Param("thirdUserId") String thirdUserId,
                                     @Param("cardIdList") List<String> cardIdList,
                                     @Param("cardType") String cardType,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate,
                                     @Param("ticketCode") String ticketCode,
                                     @Param("offset") Integer offset,
                                     @Param("limit") Integer limit);

    /**
     * 统计 IF8A-05 分页查询结果总数。
     */
    int countTransList(@Param("thirdUserId") String thirdUserId,
                       @Param("cardIdList") List<String> cardIdList,
                       @Param("cardType") String cardType,
                       @Param("startDate") String startDate,
                       @Param("endDate") String endDate,
                       @Param("ticketCode") String ticketCode);

    /**
     * 查询用户指定支付渠道在指定时间之后是否存在扣费失败订单。
     */
    int countFailedOrder(@Param("thirdUserId") String thirdUserId,
                         @Param("paymentVendor") String paymentVendor,
                         @Param("requestTime") LocalDateTime requestTime);
}
