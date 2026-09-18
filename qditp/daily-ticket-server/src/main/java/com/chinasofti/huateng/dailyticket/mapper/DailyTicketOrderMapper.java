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

    /** 按旅游票主单号查询其下全部日票子单。 */
    java.util.List<DailyTicketOrder> selectByParentOrderNo(@Param("parentOrderNo") String parentOrderNo);

    /** 运营页面按下单时间及订单号查询日票订单。 */
    java.util.List<DailyTicketRefundOrderView> selectRefundOrders(DailyTicketRefundOrderQuery query);

    /** 运营页面查询旅游票主单下的子单明细。 */
    java.util.List<DailyTicketRefundOrderView> selectTravelSubRefundOrders(@Param("parentOrderNo") String parentOrderNo);

    int updatePayRequest(DailyTicketOrder record);

    /** 支付终态条件更新，仅允许支付中订单首次进入终态。 */
    int updatePayResultIfPaying(DailyTicketOrder record);

    /** 回写支付平台原支付订单号，保留首次获取的有效值。 */
    int updatePaymentOrderNo(@Param("orderNo") String orderNo,
                             @Param("paymentOrderNo") String paymentOrderNo);

    int updateOrderStatus(@Param("orderNo") String orderNo, @Param("orderStatus") String orderStatus);
}
