package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.ticket.OrderRefundCycle;
import com.github.pagehelper.PageInfo;

public interface OrderRefundCycleService {
    ResultVO<PageInfo<OrderRefundCycle>> page(String ticketType, Integer pageNum, Integer pageSize);

    ResultVO<Void> create(OrderRefundCycle request);

    ResultVO<Void> update(String ticketType, OrderRefundCycle request);

    ResultVO<Void> delete(String ticketType);
}
