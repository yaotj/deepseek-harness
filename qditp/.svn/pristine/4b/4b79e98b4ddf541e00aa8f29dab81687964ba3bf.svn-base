package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.ticket.OrderRefundCycle;
import com.github.pagehelper.PageInfo;

/** 订单自动退款周期参数的运营维护服务。 */
public interface OrderRefundCycleService {
    /** 按票卡类型分页查询退款周期配置。 */
    ResultVO<PageInfo<OrderRefundCycle>> page(String ticketType, Integer pageNum, Integer pageSize);

    /** 为票卡类型创建退款周期配置。 */
    ResultVO<Void> create(OrderRefundCycle request);

    /** 修改指定票卡类型的退款周期，票卡类型本身不可变更。 */
    ResultVO<Void> update(String ticketType, OrderRefundCycle request);

    /** 删除指定票卡类型的退款周期配置。 */
    ResultVO<Void> delete(String ticketType);
}
