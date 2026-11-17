package com.chinasofti.huateng.dailyticket.controller.internal;

import com.chinasofti.huateng.dailyticket.service.DailyTicketPayResultNotifyService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/daily-ticket/pay")
public class PayNotifyInternalController {

    private static final Logger log = LoggerFactory.getLogger(PayNotifyInternalController.class);

    private final DailyTicketPayResultNotifyService payResultNotifyService;

    public PayNotifyInternalController(DailyTicketPayResultNotifyService payResultNotifyService) {
        this.payResultNotifyService = payResultNotifyService;
    }


    @PostMapping("/notify")
    public DailyTicketBaseResult notifyPending(@RequestParam(value = "limit", required = false) Integer limit) {
        int delivered = payResultNotifyService.deliverPending(limit == null ? 0 : limit);
        log.info("支付结果通知补偿执行完成 limit={}, delivered={}", limit, delivered);
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        result.setRetCode("0000");
        result.setRetMsg("成功");
        result.setData(delivered);
        return result;
    }
}
