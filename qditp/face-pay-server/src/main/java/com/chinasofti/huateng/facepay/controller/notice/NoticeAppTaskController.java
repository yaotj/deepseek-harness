package com.chinasofti.huateng.facepay.controller.notice;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.facepay.service.F2fNotifyDeliverer;
import com.chinasofti.huateng.facepay.service.F2fNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pay/noticeAppTask")
public class NoticeAppTaskController {

    private static final Logger log = LoggerFactory.getLogger(NoticeAppTaskController.class);

    private static final String RET_CODE_SUCCESS = "0000";

    private final F2fNotifyDeliverer deliverer;

    private final int batchLimit;

    public NoticeAppTaskController(F2fNotifyDeliverer deliverer,
                                   @Value("${f2f.notify.scanLimit:100}") int batchLimit) {
        this.deliverer = deliverer;
        this.batchLimit = batchLimit;
    }

    @PostMapping("/testtbNoticeAppTask")
    public CommonResult testtbNoticeAppTask() {
        log.info("通知重投入口探针被调用");
        return result("探针正常");
    }

    @PostMapping("/noticeTakeTicketTask")
    public CommonResult noticeTakeTicketTask() {
        return deliverByType(F2fNotifyService.TYPE_TAKE_TICKET_OK);
    }

    @PostMapping("/noticeTakeTicketFailureTask")
    public CommonResult noticeTakeTicketFailureTask() {
        return deliverByType(F2fNotifyService.TYPE_TAKE_TICKET_FAIL);
    }

    @PostMapping("/noticeRefundTask")
    public CommonResult noticeRefundTask() {
        return deliverByType(F2fNotifyService.TYPE_REFUND_RESULT);
    }

    private CommonResult deliverByType(String notifyType) {
        F2fNotifyDeliverer.DeliverStat stat = deliverer.deliverDueByType(notifyType, batchLimit);
        return result(String.format("notifyType=%s, 到期=%d, 已投递=%d, 待重试=%d, 异常=%d",
                notifyType, stat.due(), stat.delivered(), stat.retried(), stat.errored()));
    }

    private static CommonResult result(String retMsg) {
        CommonResult response = new CommonResult();
        response.setRetCode(RET_CODE_SUCCESS);
        response.setRetMsg(retMsg);
        return response;
    }
}
