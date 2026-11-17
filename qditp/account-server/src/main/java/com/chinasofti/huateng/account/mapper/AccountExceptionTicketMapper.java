package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;


@Mapper
public interface AccountExceptionTicketMapper {

    int insert(AccountExceptionTicket record);


    List<AccountExceptionTicket> selectByCondition(@Param("ticketStatus") String ticketStatus,
                                                  @Param("ticketType") String ticketType,
                                                  @Param("thirdUserId") String thirdUserId,
                                                  @Param("limit") int limit);


    int close(@Param("id") Long id,
              @Param("closedBy") String closedBy,
              @Param("closeTms") LocalDateTime closeTms);
}
