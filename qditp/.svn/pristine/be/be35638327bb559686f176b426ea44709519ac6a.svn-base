package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketOrderMapper {
    int insert(DailyTicketOrder record);

    DailyTicketOrder selectByOrderNo(@Param("orderNo") String orderNo);

    int updatePayRequest(DailyTicketOrder record);

    int updatePayResult(DailyTicketOrder record);

    int updateOrderStatus(@Param("orderNo") String orderNo, @Param("orderStatus") String orderStatus);
}
