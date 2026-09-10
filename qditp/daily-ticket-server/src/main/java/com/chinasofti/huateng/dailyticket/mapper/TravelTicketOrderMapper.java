package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TravelTicketOrderMapper {
    int insert(TravelTicketOrder record);

    TravelTicketOrder selectByOrderNo(@Param("orderNo") String orderNo);
}
