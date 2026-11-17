package com.chinasofti.huateng.account.controller.page;

import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import com.chinasofti.huateng.account.service.AccountExceptionTicketService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/page/exception-ticket")
public class AccountExceptionTicketPageController {
    private final AccountExceptionTicketService accountExceptionTicketService;

    public AccountExceptionTicketPageController(AccountExceptionTicketService accountExceptionTicketService) {
        this.accountExceptionTicketService = accountExceptionTicketService;
    }


    @GetMapping("/list")
    public ResultVO<List<AccountExceptionTicket>> list(
            @RequestParam(required = false) String ticketStatus,
            @RequestParam(required = false) String ticketType,
            @RequestParam(required = false) String thirdUserId,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        List<AccountExceptionTicket> tickets =
                accountExceptionTicketService.list(ticketStatus, ticketType, thirdUserId, limit);
        return ResultMapper.ok(tickets);
    }


    @PostMapping("/close")
    public ResultVO<Void> close(@RequestParam Long id, @RequestParam String closedBy) {
        if (id == null || !StringUtils.hasText(closedBy)) {
            return ResultMapper.illegalParams("工单ID和操作人不能为空");
        }
        if (!accountExceptionTicketService.close(id, closedBy)) {
            return ResultMapper.error("工单不存在或已关闭");
        }
        return ResultMapper.ok();
    }
}
