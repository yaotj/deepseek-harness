package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketInstanceMapper {
    int upsert(DailyTicketInstance record);

    DailyTicketInstance selectByOrderNo(@Param("orderNo") String orderNo);


    DailyTicketInstance selectByCardNum(@Param("cardNum") String cardNum);

    DailyTicketInstance selectByTicketCode(@Param("ticketCode") String ticketCode);

    int markUsed(DailyTicketInstance record);


    java.util.List<DailyTicketInstance> selectPendingAccNotice(@Param("maxTimes") int maxTimes,
                                                               @Param("limit") int limit);


    int updateAccNoticeStatus(@Param("orderNo") String orderNo,
                              @Param("accNoticeStatus") String accNoticeStatus,
                              @Param("accNoticeTimes") Integer accNoticeTimes,
                              @Param("accNoticeTime") java.util.Date accNoticeTime,
                              @Param("accNoticeMsg") String accNoticeMsg);

    DailyTicketInstance selectForEntryCheck(@Param("cardNum") String cardNum);


    int decreaseActualTimes(@Param("id") String id, @Param("updateTime") java.util.Date updateTime);


    int updateStatusIfCurrent(@Param("id") String id,
                              @Param("expectStatus") String expectStatus,
                              @Param("nextStatus") String nextStatus,
                              @Param("updateTime") java.util.Date updateTime);


    java.util.List<DailyTicketInstance> selectExpiredCandidates(@Param("nowMillis") long nowMillis,
                                                                @Param("now") java.util.Date now,
                                                                @Param("fallbackByPeriod") boolean fallbackByPeriod,
                                                                @Param("limit") int limit);
}
