package com.chinasofti.huateng.dailyticket.controller.internal;

import com.chinasofti.huateng.dailyticket.service.DailyTicketAccActiveNotifyService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ACC 日票发售通知补偿入口。 */
@RestController
@RequestMapping("/internal/daily-ticket/acc")
public class AccActiveNotifyInternalController {

    private static final Logger log = LoggerFactory.getLogger(AccActiveNotifyInternalController.class);

    private final DailyTicketAccActiveNotifyService accActiveNotifyService;

    public AccActiveNotifyInternalController(DailyTicketAccActiveNotifyService accActiveNotifyService) {
        this.accActiveNotifyService = accActiveNotifyService;
    }

    /**
     * 扫表补投日票激活后的 ACC 发售通知。
     *
     * @param limit 单批条数上限，不传按服务侧默认值
     * @return {@code retCode=0000} + {@code data} 为本批投递成功条数
     */
    @PostMapping("/active-notify")
    public DailyTicketBaseResult notifyPending(@RequestParam(value = "limit", required = false) Integer limit) {
        int delivered = accActiveNotifyService.deliverPending(limit == null ? 0 : limit);
        log.info("ACC日票发售通知补偿执行完成 limit={}, delivered={}", limit, delivered);
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        result.setRetCode("0000");
        result.setRetMsg("成功");
        result.setData(delivered);
        return result;
    }
}
