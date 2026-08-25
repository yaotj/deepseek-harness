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

    /**
     * 进站校验：查询日票实例（含countingStart/countingEnd/ticketStatus/actualTimes）。
     */
    DailyTicketInstance selectForEntryCheck(@Param("cardNum") String cardNum);

    /**
     * 计次票扣减一次可用次数（actualTimes - 1），仅用于计次票（actualTimes > 0）。
     */
    int decreaseActualTimes(@Param("cardNum") String cardNum, @Param("updateTime") java.util.Date updateTime);
}
