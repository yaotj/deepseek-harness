package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketInstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 票实例在退款链路上的状态守卫：锁票、放款收口、失败解锁。
 *
 * <p>三个方法**全部是 CAS**（{@code updateStatusIfCurrent}），只允许明确的前置状态推进，
 * 符合 AGENTS.md §5.2「状态机校验用白名单」。日票退款与旅游票整单退款都走这里，
 * 因此它先于退款聚合被抽出来 —— 否则新的退款服务只能反向依赖 {@code DailyTicketServiceImpl}。
 *
 * <p><b>本类零 {@code @Transactional}、也 NEVER 加</b>：调用方（退款链路）自身不带事务、
 * 且链路里有支付网关出网，加事务会踩 §5.2 那条「事务内 NEVER 发起 RPC」的生产事故。
 */
@Component
public class DailyTicketTicketLockWriter {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketTicketLockWriter.class);

    private final DailyTicketInstanceMapper instanceMapper;

    public DailyTicketTicketLockWriter(DailyTicketInstanceMapper instanceMapper) {
        this.instanceMapper = instanceMapper;
    }

    /**
     * 发起核验退款时把票从 {@code ACTIVATED} 锁进 {@code REFUND_LOCKED}，返回是否抢到。
     * 这是本模块唯一阻止「观察期内继续乘坐」的地方：锁上之后 {@code selectForEntryCheck} 查不到、
     * {@code markUsed} 也会被 {@link DailyTicketInstanceStatus#isLockedForRefund} 挡住。
     */
    public boolean lockForRefund(DailyTicketInstance ticket) {
        return instanceMapper.updateStatusIfCurrent(ticket.getId(),
                DailyTicketInstanceStatus.ACTIVATED, DailyTicketInstanceStatus.REFUND_LOCKED, new Date()) > 0;
    }

    /**
     * 放款成功后把票推进终态 {@code REFUNDED}。
     * 未激活票（refundType=00）在本表没有实例、CAS 影响 0 行，属正常，只记日志不报错。
     */
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

    /** 退款明确失败后把票解锁回 {@code ACTIVATED}，否则用户既没退到钱、票也被锁死。 */
    public void releaseLock(String orderNo) {
        DailyTicketInstance ticket = instanceMapper.selectByOrderNo(orderNo);
        if (ticket == null) {
            return;
        }
        instanceMapper.updateStatusIfCurrent(ticket.getId(),
                DailyTicketInstanceStatus.REFUND_LOCKED, DailyTicketInstanceStatus.ACTIVATED, new Date());
    }
}
