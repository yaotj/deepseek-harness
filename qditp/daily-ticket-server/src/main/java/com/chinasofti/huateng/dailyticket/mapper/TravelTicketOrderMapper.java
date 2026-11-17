package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TravelTicketOrderMapper {
    int insert(TravelTicketOrder record);

    TravelTicketOrder selectByOrderNo(@Param("orderNo") String orderNo);


    java.util.List<TravelTicketOrder> selectPaidNotActivated(@Param("paidBefore") java.time.LocalDateTime paidBefore,
                                                            @Param("paidAfter") java.time.LocalDateTime paidAfter,
                                                            @Param("limit") int limit);

    int updatePayRequest(TravelTicketOrder record);

    int updatePayResultIfPaying(TravelTicketOrder record);

    int updatePaySuccessByExternalSync(TravelTicketOrder record);

    int updatePaymentOrderNo(@Param("orderNo") String orderNo,
                             @Param("paymentOrderNo") String paymentOrderNo);

    int updateOrderStatus(@Param("orderNo") String orderNo, @Param("orderStatus") String orderStatus);

    int cancelIfPending(@Param("orderNo") String orderNo);

    int cancelIfPaid(@Param("orderNo") String orderNo);

    int updatePayResultIfCanceled(TravelTicketOrder record);
}
