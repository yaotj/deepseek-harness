package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.ticket.SingleTicketPurchaseLimit;
import com.chinasofti.huateng.para.mapper.ticket.SingleTicketPurchaseLimitMapper;
import com.chinasofti.huateng.para.service.SingleTicketPurchaseLimitService;
import org.springframework.stereotype.Service;

/** 单程票购买上限参数管理实现。 */
@Service
public class SingleTicketPurchaseLimitServiceImpl implements SingleTicketPurchaseLimitService {
    private static final int MIN_PURCHASE_QUANTITY = 1;
    private static final int MAX_PURCHASE_QUANTITY = 99;

    private final SingleTicketPurchaseLimitMapper singleTicketPurchaseLimitMapper;

    public SingleTicketPurchaseLimitServiceImpl(SingleTicketPurchaseLimitMapper singleTicketPurchaseLimitMapper) {
        this.singleTicketPurchaseLimitMapper = singleTicketPurchaseLimitMapper;
    }

    @Override
    public ResultVO<SingleTicketPurchaseLimit> getCurrent() {
        return ResultMapper.ok(singleTicketPurchaseLimitMapper.selectCurrent());
    }

    @Override
    public ResultVO<Void> update(SingleTicketPurchaseLimit request) {
        if (request == null || request.getMaxPurchaseQuantity() == null || request.getVersion() == null) {
            return ResultMapper.illegalParams("最大购买张数和版本号不能为空");
        }
        int quantity = request.getMaxPurchaseQuantity();
        if (quantity < MIN_PURCHASE_QUANTITY || quantity > MAX_PURCHASE_QUANTITY) {
            return ResultMapper.illegalParams("单程票最大购买张数必须在 1 到 99 之间");
        }
        if (singleTicketPurchaseLimitMapper.update(request) == 0) {
            return ResultMapper.error("参数已被其他操作更新，请重新查询后再提交");
        }
        return ResultMapper.ok();
    }
}
