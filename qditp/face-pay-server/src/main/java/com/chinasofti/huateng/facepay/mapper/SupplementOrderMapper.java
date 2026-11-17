package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.entity.SupplementOrderItem;
import com.chinasofti.huateng.facepay.entity.SupplementOrigRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SupplementOrderMapper {

    int insert(SupplementOrder record);

    SupplementOrder selectByOrderNo(@Param("orderNo") String orderNo);

    int updatePayStatusFromPending(@Param("orderNo") String orderNo,
                                   @Param("payStatus") String payStatus,
                                   @Param("remark") String remark);

    int insertItem(SupplementOrderItem record);

    List<SupplementOrderItem> selectItemsByOrderNo(@Param("orderNo") String orderNo);

    int updateItemSettleStatus(@Param("orderNo") String orderNo,
                               @Param("origOrderNo") String origOrderNo,
                               @Param("settleStatus") String settleStatus,
                               @Param("remark") String remark);

    int updatePrepayResult(@Param("orderNo") String orderNo,
                           @Param("payChannelCode") String payChannelCode,
                           @Param("merchantOrderNo") String merchantOrderNo,
                           @Param("paymentInfo") String paymentInfo);

    List<SupplementOrder> selectTimeoutPending(@Param("timeoutMinutes") int timeoutMinutes,
                                               @Param("limit") int limit);

    int closeTimeoutOrder(@Param("orderNo") String orderNo, @Param("remark") String remark);

    List<SupplementOrder> selectTimeoutProcessing(@Param("timeoutMinutes") int timeoutMinutes,
                                                  @Param("limit") int limit);

    int closeStaleProcessingOrder(@Param("orderNo") String orderNo, @Param("remark") String remark);

    int forceSuccessFromClosedOrFail(@Param("orderNo") String orderNo,
                                     @Param("remark") String remark,
                                     @Param("payChannelCode") String payChannelCode,
                                     @Param("merchantOrderNo") String merchantOrderNo,
                                     @Param("paymentInfo") String paymentInfo);

    List<SupplementOrder> selectPendingOrders(@Param("limit") int limit,
                                              @Param("closedLookbackHours") int closedLookbackHours,
                                              @Param("pendingLookbackHours") int pendingLookbackHours);

    List<SupplementOrder> selectSupplementOrderPage(@Param("orderNo") String orderNo,
                                                    @Param("cardId") String cardId,
                                                    @Param("thirdUserId") String thirdUserId,
                                                    @Param("payStatus") String payStatus,
                                                    @Param("txnDateStart") String txnDateStart,
                                                    @Param("txnDateEnd") String txnDateEnd,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    long countSupplementOrderPage(@Param("orderNo") String orderNo,
                                  @Param("cardId") String cardId,
                                  @Param("thirdUserId") String thirdUserId,
                                  @Param("payStatus") String payStatus,
                                  @Param("txnDateStart") String txnDateStart,
                                  @Param("txnDateEnd") String txnDateEnd);


    List<SupplementOrigRef> selectRefsByOrigOrderNos(@Param("origOrderNos") List<String> origOrderNos);
}
