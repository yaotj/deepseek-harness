package com.chinasofti.huateng.para.mapper.ticket;

import com.chinasofti.huateng.para.entity.ticket.OrderRefundCycle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderRefundCycleMapper {
    List<OrderRefundCycle> selectPage(@Param("ticketType") String ticketType);

    OrderRefundCycle selectByTicketType(@Param("ticketType") String ticketType);

    int insert(OrderRefundCycle record);

    int update(OrderRefundCycle record);

    int deleteByTicketType(@Param("ticketType") String ticketType);
}
