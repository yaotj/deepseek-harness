package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import com.chinasofti.huateng.account.mapper.AccountExceptionTicketMapper;
import com.chinasofti.huateng.account.service.AccountExceptionTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常工单运营查询与人工关单的实现，见 {@link AccountExceptionTicketService}。
 *
 * <p>2026-09-11 从 {@code AccountExceptionTicketPageController} 下沉，逐行照搬、行为不变：
 * 条数收敛（缺省 50、上限 200）、空串筛选项归一为 null、关单的 CAS 结果判定与两条日志都在这里。</p>
 *
 * <p><b>本类不带 `@Transactional`</b>：查询是单条 select，关单是单条 CAS UPDATE，
 * 单语句自身原子，没有第二个需要一起回滚的写。<b>NEVER 因为「像是写操作」就加事务注解</b>。</p>
 */
@Service
public class AccountExceptionTicketServiceImpl implements AccountExceptionTicketService {
    private static final Logger log = LoggerFactory.getLogger(AccountExceptionTicketServiceImpl.class);

    /** 缺省查询条数。 */
    private static final int DEFAULT_QUERY_LIMIT = 50;

    /** 单次查询条数上限。这张表只增不删、没有归档任务，NEVER 放开成全量查询。 */
    private static final int MAX_QUERY_LIMIT = 200;

    private final AccountExceptionTicketMapper accountExceptionTicketMapper;

    public AccountExceptionTicketServiceImpl(AccountExceptionTicketMapper accountExceptionTicketMapper) {
        this.accountExceptionTicketMapper = accountExceptionTicketMapper;
    }

    @Override
    public List<AccountExceptionTicket> list(String ticketStatus, String ticketType, String thirdUserId, int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_QUERY_LIMIT : Math.min(limit, MAX_QUERY_LIMIT);
        return accountExceptionTicketMapper.selectByCondition(
                trimToNull(ticketStatus), trimToNull(ticketType), trimToNull(thirdUserId), effectiveLimit);
    }

    @Override
    public boolean close(Long id, String closedBy) {
        int affected = accountExceptionTicketMapper.close(id, closedBy.trim(), LocalDateTime.now());
        if (affected == 0) {
            log.warn("异常工单关单未命中, 工单不存在或已不是OPEN, id={}, closedBy={}", id, closedBy);
            return false;
        }
        log.info("异常工单已人工关单, id={}, closedBy={}", id, closedBy);
        return true;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
