package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketInstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;


@Component
public class DailyTicketTicketLockWriter {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketTicketLockWriter.class);

    private final DailyTicketInstanceMapper instanceMapper;

    public DailyTicketTicketLockWriter(DailyTicketInstanceMapper instanceMapper) {
        this.instanceMapper = instanceMapper;
    }


    public boolean lockForRefund(DailyTicketInstance ticket) {
        return instanceMapper.updateStatusIfCurrent(ticket.getId(),
                DailyTicketInstanceStatus.ACTIVATED, DailyTicketInstanceStatus.REFUND_LOCKED, new Date()) > 0;
    }


    public void settleOnRefunded(String orderNo) {
        DailyTicketInstance ticket = instanceMapper.selectByOrderNo(orderNo);
        if (ticket == null) {
            return;
        }
        int settled = instanceMapper.updateStatusIfCurrent(ticket.getId(),
                DailyTicketInstanceStatus.REFUND_LOCKED, DailyTicketInstanceStatus.REFUNDED, new Date());
        if (settled == 0) {
            log.error("日票退款已放款但票状态不是 REFUND_LOCKED，需人工核对 orderNo={}, instanceId={}, ticketStatus={}",
                    orderNo, ticket.getId(), ticket.getTicketStatus());
        }
    }

    public void releaseLock(String orderNo) {
        DailyTicketInstance ticket = instanceMapper.selectByOrderNo(orderNo);
        if (ticket == null) {
            return;
        }
        instanceMapper.updateStatusIfCurrent(ticket.getId(),
                DailyTicketInstanceStatus.REFUND_LOCKED, DailyTicketInstanceStatus.ACTIVATED, new Date());
    }
}
