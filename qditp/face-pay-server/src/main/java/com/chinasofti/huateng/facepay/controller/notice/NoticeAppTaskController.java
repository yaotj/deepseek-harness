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

/** APP 通知重投的外部触发入口，URL 与旧模块 {@code collect-pay-server} 的 {@code NoticeAppTask} 逐字一致（{@code /pay/noticeAppTask/**} 四条）。 */
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

    /** 连通性探针，与旧模块同名端点语义一致：不碰任何表，恒返 {@code 0000}。 */
    @PostMapping("/testtbNoticeAppTask")
    public CommonResult testtbNoticeAppTask() {
        log.info("通知重投入口探针被调用");
        return result("探针正常");
    }

    /** 重投出票成功通知（IF8B-04），只投 {@code NOTIFY_TYPE='TAKE_TICKET_OK'} 的到期任务。 */
    @PostMapping("/noticeTakeTicketTask")
    public CommonResult noticeTakeTicketTask() {
        return deliverByType(F2fNotifyService.TYPE_TAKE_TICKET_OK);
    }

    /** 重投出票失败通知（IF8B-06），只投 {@code NOTIFY_TYPE='TAKE_TICKET_FAIL'} 的到期任务。 */
    @PostMapping("/noticeTakeTicketFailureTask")
    public CommonResult noticeTakeTicketFailureTask() {
        return deliverByType(F2fNotifyService.TYPE_TAKE_TICKET_FAIL);
    }

    /** 重投退款结果通知（IF8B-07），只投 {@code NOTIFY_TYPE='REFUND_RESULT'} 的到期任务。 */
    @PostMapping("/noticeRefundTask")
    public CommonResult noticeRefundTask() {
        return deliverByType(F2fNotifyService.TYPE_REFUND_RESULT);
    }

    /** 三个投递端点的公共体。 */
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
