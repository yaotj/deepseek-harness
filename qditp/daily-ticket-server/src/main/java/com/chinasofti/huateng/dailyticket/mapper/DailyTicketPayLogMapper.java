package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketPayLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketPayLogMapper {
    int insert(DailyTicketPayLog record);

    String selectLatestRefundResponseBody(@Param("orderNo") String orderNo);
}
