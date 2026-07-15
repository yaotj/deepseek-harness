package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketInstanceMapper {
    int upsert(DailyTicketInstance record);

    DailyTicketInstance selectByOrderNo(@Param("orderNo") String orderNo);

    DailyTicketInstance selectByCardNum(@Param("cardNum") String cardNum);

    int markUsed(DailyTicketInstance record);
}
