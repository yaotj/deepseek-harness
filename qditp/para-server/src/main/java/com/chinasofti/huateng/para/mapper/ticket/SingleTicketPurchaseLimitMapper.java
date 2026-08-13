package com.chinasofti.huateng.para.mapper.ticket;

import com.chinasofti.huateng.para.entity.ticket.SingleTicketPurchaseLimit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SingleTicketPurchaseLimitMapper {
    SingleTicketPurchaseLimit selectCurrent();

    /** 使用版本号更新，防止两个运营人员的修改相互覆盖。 */
    int update(SingleTicketPurchaseLimit record);
}
