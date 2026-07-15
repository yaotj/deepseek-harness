package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketRefundMapper {
    int insert(DailyTicketRefund record);

    DailyTicketRefund selectByOrderNo(@Param("orderNo") String orderNo);

    int updateResult(DailyTicketRefund record);
}
