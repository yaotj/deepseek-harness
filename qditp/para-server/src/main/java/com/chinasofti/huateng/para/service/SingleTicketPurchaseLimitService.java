package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.ticket.SingleTicketPurchaseLimit;

public interface SingleTicketPurchaseLimitService {
    ResultVO<SingleTicketPurchaseLimit> getCurrent();

    ResultVO<Void> update(SingleTicketPurchaseLimit request);
}
