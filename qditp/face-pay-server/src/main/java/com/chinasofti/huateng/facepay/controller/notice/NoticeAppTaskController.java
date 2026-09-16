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

/**
 * APP 通知重投的外部触发入口，URL 与旧模块 {@code collect-pay-server} 的
 * {@code NoticeAppTask} <b>逐字一致</b>（{@code /pay/noticeAppTask/**} 四条）。
 *
 * <h2>为什么本模块也要有这四条</h2>
 * <p>本模块的通知投递已有 {@code F2fNotifyJob}（fixedDelay 30 秒）常规兜底，比旧实现强，
 * 但**旧模块的这四个 URL 是 web-admin Quartz 与运维手工重投的既有入口**。
 * 设备与 APP 流量已于 2026-09-15 切到本模块（ADR-D85），旧模块的这四条打进来只会去扫
 * 三张空的 {@code TBL_NOTICE_APP_*} 旧表、每轮返 0 条，看着像「通知都发完了」。
 * 因此本模块补齐同名端点，落到 {@code F2F_NOTIFY_TASK}。
 *
 * <h2>与旧实现的三处差异（都是有意的）</h2>
 * <ol>
 *   <li><b>旧的三个投递方法返回 {@code void}</b>（HTTP 200 空体），调用方无法知道发了几条。
 *       本实现四条统一返 {@link CommonResult}，{@code retMsg} 带
 *       「到期/已投递/待重试/异常」四个计数，Quartz 日志里能直接看出这轮做了什么。</li>
 *   <li><b>旧实现每类通知一张表</b>，本模块是一张表 + {@code NOTIFY_TYPE} 列。
 *       但**触发粒度保持按类型分开**——运维点「重投退款通知」不该把取票通知也发一遍。</li>
 *   <li><b>旧实现的重试次数在应用侧 +1 后回写</b>，本模块由 mapper 单条 UPDATE 用
 *       {@code CASE WHEN} 判定是否 {@code GIVEUP}，因此**同一批被并发触发两次也不会突破
 *       {@code MAX_RETRY_TIMES}**。</li>
 * </ol>
 *
 * <p><b>鉴权</b>：无。与 collect-pay 的 {@code NoticeAppTask}、本模块
 * {@code /page/face-pay/orders/**} 一致，靠网络隔离。这四条<b>不改业务数据、只重发通知</b>，
 * 最坏后果是 APP 侧收到重复通知（APP MUST 按 orderNo 幂等，同 {@code F2fNotifyJob} 的多副本约束），
 * 因此风险低于 {@code /internal/app-order/**} 那类建单端点。**上线前 MUST 与其余裸端点一并补鉴权**。
 *
 * <p><b>NEVER 因为有了这四条就停掉 {@code F2fNotifyJob}</b>：外部触发是快速路径，
 * 常规收敛仍靠模块内扫表——Quartz 一旦没配，通知会永久躺在表里（旧实现正是这个坑）。
 */
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

    /**
     * 连通性探针，与旧模块同名端点语义一致：<b>不碰任何表</b>，恒返 {@code 0000}。
     *
     * <p>保留它的理由是运维用它确认「这台服务的通知重投入口通不通」，
     * 与「有没有待投递任务」解耦——投递端点返 0 条时分不清是没任务还是没连上。
     */
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

    /**
     * 三个投递端点的公共体。
     *
     * <p><b>恒返 {@code 0000}</b>：本端点的语义是「触发一轮投递」，
     * 单笔通知投递失败属正常业务分支（已按退避排下次重试），不该让调用方以为触发本身失败、
     * 从而立刻重试整轮——那只会把同一批任务重复捞出。真实结果看 {@code retMsg} 的计数与服务端日志。
     */
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
