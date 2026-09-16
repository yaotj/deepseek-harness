package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DailyTicketUsageLogMapper {

    int insert(DailyTicketUsageLog record);

    List<DailyTicketUsageLog> selectByCardNum(@Param("cardNum") String cardNum);
}
