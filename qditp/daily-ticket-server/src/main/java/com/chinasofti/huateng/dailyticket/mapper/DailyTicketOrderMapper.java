package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketOrderMapper {
    int insert(DailyTicketOrder record);

    DailyTicketOrder selectByOrderNo(@Param("orderNo") String orderNo);

    java.util.List<DailyTicketOrder> selectByParentOrderNo(@Param("parentOrderNo") String parentOrderNo);


    java.util.List<DailyTicketOrder> selectPaidNotActivated(@Param("paidBefore") java.time.LocalDateTime paidBefore,
                                                           @Param("paidAfter") java.time.LocalDateTime paidAfter,
                                                           @Param("limit") int limit);

    java.util.List<DailyTicketRefundOrderView> selectRefundOrders(DailyTicketRefundOrderQuery query);

    java.util.List<DailyTicketRefundOrderView> selectTravelSubRefundOrders(@Param("parentOrderNo") String parentOrderNo);

    int updatePayRequest(DailyTicketOrder record);

    int updatePayResultIfPaying(DailyTicketOrder record);

    int updatePaySuccessByExternalSync(DailyTicketOrder record);

    int updatePaymentOrderNo(@Param("orderNo") String orderNo,
                             @Param("paymentOrderNo") String paymentOrderNo);

    int updateOrderStatus(@Param("orderNo") String orderNo, @Param("orderStatus") String orderStatus);


    int cancelIfPending(@Param("orderNo") String orderNo);


    int cancelIfPaid(@Param("orderNo") String orderNo);

    int cancelSubOrdersByParent(@Param("parentOrderNo") String parentOrderNo);


    int updatePayResultIfCanceled(DailyTicketOrder record);
}
