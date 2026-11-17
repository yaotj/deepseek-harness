package com.chinasofti.huateng.dailyticket.service;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;


@Service
public class DailyTicketRefundQueryService {

    private final DailyTicketOrderMapper orderMapper;
    private final DailyTicketRefundMapper refundMapper;

    public DailyTicketRefundQueryService(DailyTicketOrderMapper orderMapper,
                                         DailyTicketRefundMapper refundMapper) {
        this.orderMapper = orderMapper;
        this.refundMapper = refundMapper;
    }

    public ResultVO<PageInfo<DailyTicketRefundOrderView>> pageRefundOrders(DailyTicketRefundOrderQuery query) {
        DailyTicketRefundOrderQuery safeQuery = query == null ? new DailyTicketRefundOrderQuery() : query;
        PageInfo<DailyTicketRefundOrderView> pageInfo = PageHelper
                .startPage(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()))
                .doSelectPageInfo(() -> orderMapper.selectRefundOrders(safeQuery));
        return ResultMapper.ok(pageInfo);
    }

    public ResultVO<List<DailyTicketRefundOrderView>> listTravelSubRefundOrders(String parentOrderNo) {
        if (!StringUtils.hasText(parentOrderNo)) {
            return ResultMapper.illegalParams("旅游票主单号不能为空");
        }
        return ResultMapper.ok(orderMapper.selectTravelSubRefundOrders(parentOrderNo));
    }

    public ResultVO<PageInfo<DailyTicketRefundView>> pageRefundRecords(DailyTicketRefundQuery query) {
        DailyTicketRefundQuery safeQuery = query == null ? new DailyTicketRefundQuery() : query;
        PageInfo<DailyTicketRefundView> pageInfo = PageHelper
                .startPage(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()))
                .doSelectPageInfo(() -> refundMapper.selectRefunds(safeQuery));
        return ResultMapper.ok(pageInfo);
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
