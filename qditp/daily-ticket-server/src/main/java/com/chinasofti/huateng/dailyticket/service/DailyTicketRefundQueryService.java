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

/**
 * 日票退款运营页的只读查询（读模型）。
 *
 * <p>从 {@code DailyTicketServiceImpl} 拆出，只依赖两个 mapper：**只读、不碰任何状态机、不出网**。
 * 与退款写链路正交，运营页查询出问题（如 Oracle 别名 / WallFilter 那类只在运行时炸的 SQL 缺陷）
 * 不会牵动交易链路。
 *
 * <p><b>NEVER 在本类里加任何写操作</b>——退款的发起 / 重试 / 重提交仍归
 * {@code DailyTicketService}，本类只做分页与列表查询。
 */
@Service
public class DailyTicketRefundQueryService {

    private final DailyTicketOrderMapper orderMapper;
    private final DailyTicketRefundMapper refundMapper;

    public DailyTicketRefundQueryService(DailyTicketOrderMapper orderMapper,
                                         DailyTicketRefundMapper refundMapper) {
        this.orderMapper = orderMapper;
        this.refundMapper = refundMapper;
    }

    /** 可退款订单分页（日票单 + 旅游票主单，强制 {@code orderType=1} 由 Controller 侧保证）。 */
    public ResultVO<PageInfo<DailyTicketRefundOrderView>> pageRefundOrders(DailyTicketRefundOrderQuery query) {
        DailyTicketRefundOrderQuery safeQuery = query == null ? new DailyTicketRefundOrderQuery() : query;
        PageInfo<DailyTicketRefundOrderView> pageInfo = PageHelper
                .startPage(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()))
                .doSelectPageInfo(() -> orderMapper.selectRefundOrders(safeQuery));
        return ResultMapper.ok(pageInfo);
    }

    /** 旅游票主单下的日票子单列表（子单退款页用）。 */
    public ResultVO<List<DailyTicketRefundOrderView>> listTravelSubRefundOrders(String parentOrderNo) {
        if (!StringUtils.hasText(parentOrderNo)) {
            return ResultMapper.illegalParams("旅游票主单号不能为空");
        }
        return ResultMapper.ok(orderMapper.selectTravelSubRefundOrders(parentOrderNo));
    }

    /** 退款记录分页。 */
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
