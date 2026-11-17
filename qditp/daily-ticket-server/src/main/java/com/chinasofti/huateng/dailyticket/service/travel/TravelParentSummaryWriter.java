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

    public void refreshBySubOrder(String subOrderNo) {
        DailyTicketOrder subOrder = orderMapper.selectByOrderNo(subOrderNo);
        if (subOrder == null || !StringUtils.hasText(subOrder.getParentOrderNo())) {
            return;
        }
        refresh(subOrder.getParentOrderNo());
    }


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
