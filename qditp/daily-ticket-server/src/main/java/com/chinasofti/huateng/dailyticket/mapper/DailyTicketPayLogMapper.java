package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketPayLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketPayLogMapper {
    int insert(DailyTicketPayLog record);

    /** 查询最近一次退款提交的网关响应，用于为历史退款记录补录支付平台退款单号。 */
    String selectLatestRefundResponseBody(@Param("orderNo") String orderNo);
}
