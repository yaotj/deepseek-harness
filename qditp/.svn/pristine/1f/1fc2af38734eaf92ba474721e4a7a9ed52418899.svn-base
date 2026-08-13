package com.chinasofti.huateng.para.mapper.ticket;

import com.chinasofti.huateng.para.entity.ticket.OrderRefundCycle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** 订单自动退款周期参数表的数据访问接口。 */
public interface OrderRefundCycleMapper {
    /** 按票卡类型查询配置列表。 */
    List<OrderRefundCycle> selectPage(@Param("ticketType") String ticketType);

    /** 根据票卡类型查询单条配置。 */
    OrderRefundCycle selectByTicketType(@Param("ticketType") String ticketType);

    /** 新增配置。 */
    int insert(OrderRefundCycle record);

    /** 更新周期和备注。 */
    int update(OrderRefundCycle record);

    /** 根据票卡类型删除配置。 */
    int deleteByTicketType(@Param("ticketType") String ticketType);
}
