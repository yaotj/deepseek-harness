package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户域异常工单持久化。
 */
@Mapper
public interface AccountExceptionTicketMapper {
    /**
     * 开单。
     */
    int insert(AccountExceptionTicket record);

    /**
     * 运营查询：按状态 / 类型 / 三方用户号筛选，按 {@code CREATE_TMS} 倒序，最多 {@code limit} 条。
     */
    List<AccountExceptionTicket> selectByCondition(@Param("ticketStatus") String ticketStatus,
                                                  @Param("ticketType") String ticketType,
                                                  @Param("thirdUserId") String thirdUserId,
                                                  @Param("limit") int limit);

    /**
     * CAS 关单：仅 {@code TICKET_STATUS = 'OPEN'} 时生效，写入 {@code CLOSE_TMS} 与 {@code CLOSED_BY}。
     *
     * @return 影响行数；0 表示该 ID 不存在或已不是 {@code OPEN}
     */
    int close(@Param("id") Long id,
              @Param("closedBy") String closedBy,
              @Param("closeTms") LocalDateTime closeTms);
}
