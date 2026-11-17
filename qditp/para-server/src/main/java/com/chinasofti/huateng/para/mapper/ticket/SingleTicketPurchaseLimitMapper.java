package com.chinasofti.huateng.para.mapper.ticket;

import com.chinasofti.huateng.para.entity.ticket.SingleTicketPurchaseLimit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SingleTicketPurchaseLimitMapper {
    SingleTicketPurchaseLimit selectCurrent();

    int update(SingleTicketPurchaseLimit record);
}
