package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketInstanceMapper {
    int upsert(DailyTicketInstance record);

    DailyTicketInstance selectByOrderNo(@Param("orderNo") String orderNo);

    DailyTicketInstance selectByCardNum(@Param("cardNum") String cardNum);

    /**
     * 按日票票号查实例，服务 IF8A-05 / IF8A-34 的购票支付信息回填。
     *
     * <p><b>入参用票号而不是卡号</b>：一张卡可以先后买过多张日票，按卡号查会命中历史多单
     * （{@link #selectByCardNum} 就有这个性质），票号才唯一对应「某一趟行程用的那张票」。</p>
     *
     * <p>2026-09-15 实测 {@code DAILY_TICKET_INSTANCE} 共 12 行、{@code TICKET_CODE} 12 个不重复、
     * 无空值，但**库里没有唯一索引保证它唯一**，因此 SQL 侧显式取最新一行、
     * <b>NEVER 去掉那个 {@code FETCH FIRST 1 ROWS ONLY}</b> —— 真出现重复票号时
     * MyBatis 会抛 {@code TooManyResultsException}，而本查询只是详情页的富化步骤，
     * 不该因为它把整个 IF8A-34 打挂。</p>
     */
    DailyTicketInstance selectByTicketCode(@Param("ticketCode") String ticketCode);

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
