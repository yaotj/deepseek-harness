package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.entity.AccountExceptionTicket;

import java.util.List;


public interface AccountExceptionTicketService {

    List<AccountExceptionTicket> list(String ticketStatus, String ticketType, String thirdUserId, int limit);


    boolean close(Long id, String closedBy);
}
