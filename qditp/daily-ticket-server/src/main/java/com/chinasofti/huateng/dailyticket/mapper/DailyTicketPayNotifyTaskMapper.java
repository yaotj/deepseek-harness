package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketPayNotifyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface DailyTicketPayNotifyTaskMapper {
    int insertIfAbsent(DailyTicketPayNotifyTask task);

    DailyTicketPayNotifyTask selectByOrderNo(@Param("orderNo") String orderNo);

    List<DailyTicketPayNotifyTask> selectPendingNotify(@Param("maxTimes") int maxTimes,
                                                       @Param("limit") int limit);

    int updateNotifyStatus(@Param("orderNo") String orderNo,
                           @Param("notifyStatus") String notifyStatus,
                           @Param("notifyTimes") Integer notifyTimes,
                           @Param("notifyTime") Date notifyTime,
                           @Param("notifyMsg") String notifyMsg);
}
