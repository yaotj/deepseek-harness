package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.entity.AccountExceptionTicket;

import java.util.List;

/**
 * 账户域异常工单的运营查询与人工关单。
 */
public interface AccountExceptionTicketService {
    /**
     * 按条件查询工单。
     */
    List<AccountExceptionTicket> list(String ticketStatus, String ticketType, String thirdUserId, int limit);

    /**
     * 人工关单，CAS 只接受 {@code OPEN}。
     *
     * @return {@code true} 表示本次真的把一行从 OPEN 改成了 CLOSED；{@code false} 表示工单不存在或已不是 OPEN
     */
    boolean close(Long id, String closedBy);
}
