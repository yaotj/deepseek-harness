package com.chinasofti.huateng.dailyticket.controller.internal;

import com.chinasofti.huateng.dailyticket.service.DailyTicketRefundNotifyService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** IF8B-04 退款结果通知的补偿入口。 */
@RestController
@RequestMapping("/internal/daily-ticket/refund")
public class RefundNotifyInternalController {

    private static final Logger log = LoggerFactory.getLogger(RefundNotifyInternalController.class);

    private final DailyTicketRefundNotifyService refundNotifyService;

    public RefundNotifyInternalController(DailyTicketRefundNotifyService refundNotifyService) {
        this.refundNotifyService = refundNotifyService;
    }

    /**
     * 扫表补投退款结果通知。
     *
     * @param limit 单批条数上限，不传按服务侧默认值
     * @return {@code retCode=0000} + {@code data} 为本批投递成功条数
     */
    @PostMapping("/notify")
    public DailyTicketBaseResult notifyPending(@RequestParam(value = "limit", required = false) Integer limit) {
        int delivered = refundNotifyService.deliverPending(limit == null ? 0 : limit);
        log.info("退款结果通知补偿执行完成 limit={}, delivered={}", limit, delivered);
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        result.setRetCode("0000");
        result.setRetMsg("成功");
        result.setData(delivered);
        return result;
    }
}
