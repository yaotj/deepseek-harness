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

    int forceSuccessFromClosedOrFail(@Param("orderNo") String orderNo,
                                     @Param("remark") String remark,
                                     @Param("payChannelCode") String payChannelCode,
                                     @Param("merchantOrderNo") String merchantOrderNo,
                                     @Param("paymentInfo") String paymentInfo);

    List<SupplementOrder> selectPendingOrders(@Param("limit") int limit,
                                             @Param("closedLookbackHours") int closedLookbackHours);

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

    /**
     * 综管台扣费明细页回填用：按一批**原扣费订单号**反查补款单。
     *
     * <p>走 {@code IDX_SUPPLEMENT_ITEM_ORIG}（{@code SUPPLEMENT_ORDER_ITEM.ORIG_ORDER_NO}）驱动，
     * 再回连补款单主表取金额/渠道/状态。调用方 MUST 保证列表非空且已限长。
     */
    List<SupplementOrigRef> selectRefsByOrigOrderNos(@Param("origOrderNos") List<String> origOrderNos);
}
