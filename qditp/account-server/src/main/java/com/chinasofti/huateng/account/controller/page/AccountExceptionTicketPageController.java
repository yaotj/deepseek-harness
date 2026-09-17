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

/**
 * 账户域异常工单的运营查询与人工关单。
 */
@RestController
@RequestMapping("/page/exception-ticket")
public class AccountExceptionTicketPageController {
    private final AccountExceptionTicketService accountExceptionTicketService;

    public AccountExceptionTicketPageController(AccountExceptionTicketService accountExceptionTicketService) {
        this.accountExceptionTicketService = accountExceptionTicketService;
    }

    /**
     * 工单列表。
     *
     * @param limit 条数上限，缺省 50、上限 200，收敛规则在 service 层
     */
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

    /**
     * 人工关单。
     *
     * @param id       工单 ID
     * @param closedBy 操作人。当前无鉴权，这个值完全由调用方自述，MUST 只当线索
     */
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
