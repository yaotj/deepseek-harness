package com.chinasofti.huateng.dailyticket.service.travel;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketInstanceStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 旅游票主单汇总状态的唯一收口：按子单的票状态与退款状态重算主单 {@code ORDER_STATUS}。
 *
 * <p>抽出它的判据不是「代码长」，而是**它被两个域同时调用**：
 * 退款域（{@code markTravelRefunded} / {@code markTravelRefundFailed}）与
 * 票实例域（出站扣次 {@code markUsed}、票状态推进）。
 * 按 `docs/domain/README.md` 判据 3「热路径写入定 owner」，主单汇总的写入方只能有一个，
 * 否则退款域与票实例域会各自推一版汇总状态、互相覆盖。
 *
 * <p>主单 {@code TRAVEL_TICKET_ORDER} 是聚合壳，{@code PAY_STATUS} 永不回写（属当前设计非缺陷，
 * 见 AGENTS.md §2.2.2 对账那条）；因此这里**只在主单已 `PAID` 时才重算**，
 * 未支付主单一律不碰 —— NEVER 去掉这道前置判断。
 */
@Component
public class TravelParentSummaryWriter {

    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketOrderMapper orderMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketRefundMapper refundMapper;

    public TravelParentSummaryWriter(TravelTicketOrderMapper travelOrderMapper,
                                     DailyTicketOrderMapper orderMapper,
                                     DailyTicketInstanceMapper instanceMapper,
                                     DailyTicketRefundMapper refundMapper) {
        this.travelOrderMapper = travelOrderMapper;
        this.orderMapper = orderMapper;
        this.instanceMapper = instanceMapper;
        this.refundMapper = refundMapper;
    }

    /** 由子单号反查主单后重算；子单没有主单（普通日票）时直接返回。 */
    public void refreshBySubOrder(String subOrderNo) {
        DailyTicketOrder subOrder = orderMapper.selectByOrderNo(subOrderNo);
        if (subOrder == null || !StringUtils.hasText(subOrder.getParentOrderNo())) {
            return;
        }
        refresh(subOrder.getParentOrderNo());
    }

    /**
     * 重算主单汇总状态。判定优先级 <b>NEVER 调换</b>：
     * 全退 &gt; 部分退 &gt; 全用 &gt; 部分用 &gt; PAID ——
     * 退款语义优先于使用语义，否则「用了一张又全退」会被算成 `PARTIAL_USED`、对账取不到退款。
     */
    public void refresh(String parentOrderNo) {
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null || !"PAID".equals(parent.getPayStatus())) {
            return;
        }
        List<DailyTicketOrder> children = orderMapper.selectByParentOrderNo(parentOrderNo);
        if (children == null || children.isEmpty()) {
            return;
        }
        int used = 0;
        int refunded = 0;
        for (DailyTicketOrder child : children) {
            DailyTicketInstance ticket = instanceMapper.selectByOrderNo(child.getOrderNo());
            if (ticket != null && (DailyTicketInstanceStatus.USED.equals(ticket.getTicketStatus())
                    || DailyTicketInstanceStatus.EXPIRED.equals(ticket.getTicketStatus()))) {
                used++;
            }
            DailyTicketRefund childRefund = refundMapper.selectByOrderNo(child.getOrderNo());
            if (childRefund != null && "REFUNDED".equals(childRefund.getRefundStatus())) {
                refunded++;
            }
        }
        String summaryStatus = "PAID";
        if (refunded == children.size()) {
            summaryStatus = "REFUNDED";
        } else if (refunded > 0) {
            summaryStatus = "PARTIAL_REFUNDED";
        } else if (used == children.size()) {
            summaryStatus = "USED";
        } else if (used > 0) {
            summaryStatus = "PARTIAL_USED";
        }
        travelOrderMapper.updateOrderStatus(parentOrderNo, summaryStatus);
    }
}
