package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.ticket.SingleTicketPurchaseLimit;
import com.chinasofti.huateng.para.service.SingleTicketPurchaseLimitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 单程票最大购买张数后台参数接口。 */
@RestController
@RequestMapping("/page/single-ticket-purchase-limit")
public class SingleTicketPurchaseLimitController {
    private final SingleTicketPurchaseLimitService singleTicketPurchaseLimitService;

    public SingleTicketPurchaseLimitController(SingleTicketPurchaseLimitService singleTicketPurchaseLimitService) {
        this.singleTicketPurchaseLimitService = singleTicketPurchaseLimitService;
    }

    @GetMapping
    public ResultVO<SingleTicketPurchaseLimit> getCurrent() {
        return singleTicketPurchaseLimitService.getCurrent();
    }

    @PutMapping
    public ResultVO<Void> update(@RequestBody SingleTicketPurchaseLimit request) {
        return singleTicketPurchaseLimitService.update(request);
    }
}
