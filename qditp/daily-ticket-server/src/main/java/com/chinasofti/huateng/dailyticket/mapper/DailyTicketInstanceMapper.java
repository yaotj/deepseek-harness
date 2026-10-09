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

    /**
     * 捞待投递的 ACC 发售通知（扫表补偿入口）。
     */
    java.util.List<DailyTicketInstance> selectPendingAccNotice(@Param("maxTimes") int maxTimes,
                                                               @Param("limit") int limit);

    /**
     * 回写 ACC 发售通知投递状态。
     */
    int updateAccNoticeStatus(@Param("orderNo") String orderNo,
                              @Param("accNoticeStatus") String accNoticeStatus,
                              @Param("accNoticeTimes") Integer accNoticeTimes,
                              @Param("accNoticeTime") java.util.Date accNoticeTime,
                              @Param("accNoticeMsg") String accNoticeMsg);

    /** 进站校验：查询日票实例（含countingStart/countingEnd/ticketStatus/actualTimes）。 */
    DailyTicketInstance selectForEntryCheck(@Param("cardNum") String cardNum);

    /**
     * 计次票扣减一次可用次数（actualTimes - 1），仅用于计次票（actualTimes > 0）。
     * 按主键扣，NEVER 改回按 CARD_NUM：一卡多张可用计次票时会让每张各减 1 次，属资损。
     */
    int decreaseActualTimes(@Param("id") String id, @Param("updateTime") java.util.Date updateTime);

    /**
     * 票状态 CAS 推进：只在当前状态等于 expectStatus 时才改成 nextStatus，返回影响行数。
     * NEVER 复用 {@link #markUsed}：那条语句会连带覆盖 COUNTING_END / FIRST_USE_TIME / ACC_NOTICE_*，
     * 用它锁票会把出站信息一并抹掉。
     *
     * <p>两个调用方：退款链路的锁票 / 放款 / 解锁（{@code DailyTicketTicketLockWriter}），
     * 以及有效期过期收敛（{@code DailyTicketExpireService}，只做 ACTIVATED|USED -> EXPIRED）。
     * 过期收敛依赖 expectStatus 这道 CAS 把 REFUND_LOCKED / REFUNDED 挡在外面，
     * **NEVER 把它改成按 ID 无条件 UPDATE** —— 那会把退款观察期内的票改成过期、退款链路随后解锁不回来。
     */
    int updateStatusIfCurrent(@Param("id") String id,
                              @Param("expectStatus") String expectStatus,
                              @Param("nextStatus") String nextStatus,
                              @Param("updateTime") java.util.Date updateTime);

    /**
     * 捞「有效期已过、但状态还停在可用态」的票，供过期收敛任务逐条 CAS 推进成 EXPIRED。
     *
     * <p>判据两支，NEVER 只留第一支：
     * <ul>
     *     <li>COUNTING_END 非空 ⇒ 按它比（权威值，来自 APP 首次使用通知 IF8A-33 或闸机出站上送）；</li>
     *     <li>COUNTING_END 为空 ⇒ 回退 ACTIVATE_TIME + PERIOD 天。激活（IF8A-32）根本不写
     *         COUNTING_END（DailyTicketActivateReqDTO 没有这个字段），因此**激活后从未乘车的票
     *         COUNTING_END 恒为空**，少了这一支它们永远不会过期。回退支可用 fallbackByPeriod 关掉。</li>
     * </ul>
     *
     * <p>状态白名单固定 ACTIVATED / USED（与 selectForEntryCheck 同一组），
     * REFUND_LOCKED / REFUNDED / 已 EXPIRED 一律不捞。
     */
    java.util.List<DailyTicketInstance> selectExpiredCandidates(@Param("nowMillis") long nowMillis,
                                                                @Param("now") java.util.Date now,
                                                                @Param("fallbackByPeriod") boolean fallbackByPeriod,
                                                                @Param("limit") int limit);
}
