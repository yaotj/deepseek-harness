package com.chinasofti.huateng.alipay.paysign.controller.task;

import com.chinasofti.huateng.alipay.paysign.service.impl.refund.RefundQueryCompensationService;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.TxnRefundQueryCompensationService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行「退款回查」补偿内部接口。
 *
 * <p>补的缺口：{@code pay.center.refund-query-url} 与 {@code PayCenterClient.refundQuery} 早就在、
 * 却**零调用方**，于是退款申请里「支付中心没答 / 拒绝」那支落下的 {@code PROCESSING} 明细没人回查、
 * 只能人工核。实现见 {@link RefundQueryCompensationService}。
 *
 * <p>本包（{@code controller.task}）的判据是「直接调用方只有 web-admin 的 Quartz、没有服务间调用方」，
 * 与 {@code AlipayChannelSyncInternalController} 同族（那份类注释里有三个子包的完整分界，
 * <b>NEVER 把本端点搬去 {@code controller.internal}</b>）。
 *
 * <p><b>驱动源</b>：`sys_job` **340「支付宝退款回查补偿」cron `0 0/10 * * * ?`**（2026-09-21 由 137 改号为 340）
 * （任务 Bean `alipayRefundQueryQuartzTask.compensate()`，迁移脚本
 * `web-server/web-quartz/src/main/resources/sql/web-quartz-alipay-refund-query-job-migration.sql`）。
 * <b>cron 间隔 MUST 大于服务侧的静默期 5 分钟</b>，否则同一行会在正常回调还没到达时被反复回查。
 * 「代码写了」不等于「补偿在跑」—— 判据是 {@code SYS_JOB_LOG} 有没有记录，MUST 现查、NEVER 引用本行。
 *
 * <p><b>本端点当前无鉴权</b>，与 §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」冲突。
 * 这与同模块其余 `/internal/**` 端点是同一现状、属测试期既有降级，上线前 MUST 一并补上，
 * <b>NEVER 在这里自造一套签名</b>。
 */
@RestController
@RequestMapping("/internal/alipay/refund")
public class AlipayRefundQueryInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayRefundQueryInternalController.class);

    private final RefundQueryCompensationService refundQueryCompensationService;
    private final TxnRefundQueryCompensationService txnRefundQueryCompensationService;

    public AlipayRefundQueryInternalController(RefundQueryCompensationService refundQueryCompensationService,
                                               TxnRefundQueryCompensationService txnRefundQueryCompensationService) {
        this.refundQueryCompensationService = refundQueryCompensationService;
        this.txnRefundQueryCompensationService = txnRefundQueryCompensationService;
    }

    /**
     * 扫一批停在 {@code PROCESSING} 的退款明细并逐条回查收口。
     *
     * <p><b>两张表各扫一轮、合并计数</b>：旧表 {@code ALIPAY_REFUND_LOG}
     * （{@link RefundQueryCompensationService}）+ 新表 {@code ALIPAY_REFUND_TXN_DETAIL}
     * （{@link TxnRefundQueryCompensationService}）。一个退款单号只存在于两张表之一，
     * 因此两轮之间没有交集、也没有顺序依赖。<b>NEVER 只扫其中一张</b> ——
     * 新表那条链路（`/internal/alipay/payment/requestTxnRefund`）在
     * {@code pay.center.refund-notify-url} 为空时压根收不到回调，回查是它唯一的收口路径。
     *
     * <p>无入参：窗口、静默期与批量都在服务侧，**NEVER 改成让调用方传** ——
     * 那等于把「一次扫多少 / 多久前的单子」交给 Quartz 配置页面。
     *
     * <p><b>恒返 0000</b>（含「一条都没收口」）：本轮没收口不是调用失败，扫描结果写在 {@code retMsg} 里、
     * 落进 `SYS_JOB_LOG` 的 `JOB_MESSAGE`。<b>NEVER 改成「有未收口就返 9999」</b> ——
     * 回查不到终态是常态（对端还在处理），报错只会让调度日志长期一片红、真故障反而看不出来。
     *
     * <p><b>但「整轮扫表本身炸了」仍会让本端点返 UUID retCode</b>（如 SQL 被 Druid WallFilter 拒）：
     * 两轮各自的 `OutboxScan` 只兜住**单条**异常。此时先跑完的那一轮其实已逐条自动提交、
     * 不会回滚，**排查 MUST 去看服务端日志那行原文，NEVER 据「端点返了 UUID」就认为这一轮一条都没收口**。
     */
    @PostMapping("/compensateQuery")
    public AlipayCommonResponse compensateQuery() {
        log.info("收到退款回查补偿请求");
        OutboxScan.Result legacyScan = refundQueryCompensationService.compensate();
        OutboxScan.Result txnScan = txnRefundQueryCompensationService.compensate();
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("旧表扫描 " + legacyScan.scanned() + " 条、收口 " + legacyScan.success()
                + " 条、仍处理中 " + legacyScan.failed() + " 条；新表扫描 " + txnScan.scanned()
                + " 条、收口 " + txnScan.success() + " 条、仍处理中 " + txnScan.failed() + " 条");
        return response;
    }
}
