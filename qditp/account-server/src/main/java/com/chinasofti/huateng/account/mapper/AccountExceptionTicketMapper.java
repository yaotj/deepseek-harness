package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户域异常工单持久化。
 * <p>
 * <b>关单只能由人工触发</b>：{@link #close} 的唯一调用方是运营后台的关单端点
 * （{@code AccountExceptionTicketPageController}），<b>NEVER 让补偿任务或任何自动流程调它</b> ——
 * 工单存在的意义就是「本域已经自愈不了、必须有人看过」，自动关单等于把告警静音。
 * <p>
 * 2026-09-11 之前本接口只有 {@link #insert}、注释写着「刻意不提供 update / close」，
 * 那是因为当时关单只能人工连库 {@code UPDATE}；现补齐查询与 CAS 关单两个方法，
 * 「不自动关单」这一条约束不变。
 */
@Mapper
public interface AccountExceptionTicketMapper {

    /**
     * 开单。<b>幂等靠唯一索引 {@code UK_ACCT_EXC_TICKET_TYPE_KEY (TICKET_TYPE, BIZ_KEY)}</b>：
     * 同一笔重复开单会抛 {@code DuplicateKeyException}，调用方 **MUST** 捕获并当成「已有工单」正常继续，
     * <b>NEVER 让它冒泡打断补偿批次</b> —— 补偿任务每 5 分钟重扫，达上限的行每轮都会走到这里。
     * <p>
     * ID 走 {@code SEQ_ACCOUNT_EXCEPTION_TICKET}，在 XML 里用 {@code <selectKey order="BEFORE">} 取，
     * <b>NEVER 改成 {@code useGeneratedKeys}</b> —— 本项目 Oracle 表上会抛 {@code MyBatisSystemException}。
     */
    int insert(AccountExceptionTicket record);

    /**
     * 运营查询：按状态 / 类型 / 三方用户号筛选，按 {@code CREATE_TMS} 倒序，最多 {@code limit} 条。
     *
     * <p>三个筛选项都可为空（不传即不过滤）。只传 {@code ticketStatus} 时命中
     * {@code IDX_ACCT_EXC_TICKET_STATUS (TICKET_STATUS, CREATE_TMS)}。</p>
     *
     * <p><b>MUST 带 {@code limit}</b>：这张表只增不删，没有归档任务，全量捞出会随时间无界增长。</p>
     */
    List<AccountExceptionTicket> selectByCondition(@Param("ticketStatus") String ticketStatus,
                                                  @Param("ticketType") String ticketType,
                                                  @Param("thirdUserId") String thirdUserId,
                                                  @Param("limit") int limit);

    /**
     * CAS 关单：仅 {@code TICKET_STATUS = 'OPEN'} 时生效，写入 {@code CLOSE_TMS} 与 {@code CLOSED_BY}。
     *
     * <p>白名单只有 {@code OPEN} 一个前置状态，因此重复关单返回 0 而不是静默成功 ——
     * 调用方 <b>MUST</b> 据此把「工单不存在 / 已关闭」与「本次关单成功」区分开，
     * <b>NEVER 无条件返回成功</b>，否则两个运营同时点关单时都会看到成功、无法追溯是谁处理的。</p>
     *
     * @return 影响行数；0 表示该 ID 不存在或已不是 {@code OPEN}
     */
    int close(@Param("id") Long id,
              @Param("closedBy") String closedBy,
              @Param("closeTms") LocalDateTime closeTms);
}
