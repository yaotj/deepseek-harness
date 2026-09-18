package com.chinasofti.huateng.dailyticket.mapper;

import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyTicketInstanceMapper {
    int upsert(DailyTicketInstance record);

    DailyTicketInstance selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按卡号取最新一张实例（CREATE_TIME desc + fetch first 1）。
     * 一张卡在本表可以有多行（重复激活 / 历史已过期），SQL 已收窄，NEVER 退回无 order by 的写法：
     * selectOne 语义遇多行会抛 TooManyResultsException，扣次与票号查询整条 500。
     */
    DailyTicketInstance selectByCardNum(@Param("cardNum") String cardNum);

    /** 按日票票号查实例，服务 IF8A-05 / IF8A-34 的购票支付信息回填。 */
    DailyTicketInstance selectByTicketCode(@Param("ticketCode") String ticketCode);

    /** 按主键更新，NEVER 改回按 CARD_NUM：一卡多实例时会污染同卡的其他票。 */
    int markUsed(DailyTicketInstance record);

    /** 进站校验：查询日票实例（含countingStart/countingEnd/ticketStatus/actualTimes）。 */
    DailyTicketInstance selectForEntryCheck(@Param("cardNum") String cardNum);

    /**
     * 计次票扣减一次可用次数（actualTimes - 1），仅用于计次票（actualTimes > 0）。
     * 按主键扣，NEVER 改回按 CARD_NUM：一卡多张可用计次票时会让每张各减 1 次，属资损。
     */
    int decreaseActualTimes(@Param("id") String id, @Param("updateTime") java.util.Date updateTime);

    /**
     * 退款链路专用的票状态 CAS 推进：只在当前状态等于 expectStatus 时才改成 nextStatus，返回影响行数。
     * NEVER 复用 {@link #markUsed}：那条语句会连带覆盖 COUNTING_END / FIRST_USE_TIME / ACC_NOTICE_*，
     * 用它锁票会把出站信息一并抹掉。
     */
    int updateStatusIfCurrent(@Param("id") String id,
                              @Param("expectStatus") String expectStatus,
                              @Param("nextStatus") String nextStatus,
                              @Param("updateTime") java.util.Date updateTime);
}
