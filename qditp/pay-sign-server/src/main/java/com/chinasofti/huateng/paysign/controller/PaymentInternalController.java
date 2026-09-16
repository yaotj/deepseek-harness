package com.chinasofti.huateng.paysign.controller;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.service.RefundDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ITP 支付（含退款）内部接口控制器。
 *
 * <p><b>为什么新起 {@code /internal/payment} 而不是塞进已有的两个前缀</b>：本模块原有 7 个
 * {@code /internal/**} 补偿端点本就分在两个前缀下（{@code /internal/paySign} 2 个签约通知补偿、
 * {@code /internal/termination} 5 个解约相关），前缀是按**业务域**分的。退款属支付域，
 * 塞进 `/internal/paySign`（类注释写明「签约内部接口」）或 `/internal/termination` 都会让
 * 「按前缀就能看出归谁」这条约定作废。<b>NEVER 把支付域的补偿端点挂到那两个前缀下。</b></p>
 *
 * <p>返回结构复用 {@code model} 模块的 {@link CompensateNotifyRespDTO}，与另外两个补偿端点
 * 一字不差（{@code scanned} / {@code submitted} / {@code skipped}），web-admin 侧不必再认一种形状。
 * <b>NEVER 在本模块新建同形副本</b>（AGENTS.md §5.1）。注意该类的 javadoc 目前只点名了
 * 签约与解约两个端点，措辞待主流程随 Quartz 接线一并补上本端点。</p>
 */
@RestController
@RequestMapping("/internal/payment")
public class PaymentInternalController {

    private static final Logger log = LoggerFactory.getLogger(PaymentInternalController.class);

    private final RefundDomainService refundDomainService;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public PaymentInternalController(RefundDomainService refundDomainService) {
        this.refundDomainService = refundDomainService;
    }

    /**
     * 退款回查补偿：无入参，内部扫一批停在 {@code PROCESSING} 的 {@code PAY_REFUND_DETAIL}，
     * 逐条拿支付中心 §3.2 refundQuery 回查并收口，收口成功后重算原支付订单的退款汇总。
     * 调用方可反复调用直到 {@code scanned} 为 0。
     *
     * <p><b>触发方是 web-admin 的 Quartz {@code sys_job}</b>，本模块 NEVER 自带 {@code @Scheduled}
     * （全模块一个都没有，见 AGENTS.md §2.2.1；排查「退款回查有没有跑」MUST 查 {@code SYS_JOB_LOG}，
     * NEVER 在本模块里找 {@code @Scheduled}）。</p>
     *
     * <p><b>它是 {@code requestRefund} 摘掉 {@code @Transactional} 的配套补偿</b>（批次 5B / ADR-D8）：
     * 那条链路是「留痕 → 出网 → 回写」，最坏停在「退款请求已发给支付中心、本地 PROCESSING」，
     * 只有本端点能把它推到终态。<b>停用本任务等于让那批单子永久悬挂</b>，NEVER 只是「先关掉看看」。</p>
     *
     * <p>单条是否收口以 {@code PAY_REFUND_DETAIL.REFUND_STATUS} 为准，
     * 不要用返回的 {@code submitted} 判断某一笔的结果。</p>
     *
     * <p>⚠️ 与 {@code /internal/**} 下其余接口一样，本接口**没有鉴权**（模块无 spring-security、
     * 无全局拦截器，属已登记的 P0）。它会按库里的退款单去支付中心发只读查询并改本地退款状态，
     * 因此上线前 MUST 确认该端口不对外暴露；补鉴权时 MUST 与 {@code /internal/paySign/**}、
     * {@code /internal/termination/**} 一起做，NEVER 在这里自造签名逻辑。</p>
     */
    @PostMapping("/compensateRefundQuery")
    public CompensateNotifyRespDTO compensateRefundQuery() {
        log.info("收到退款回查补偿请求");
        return refundDomainService.compensateRefundQuery();
    }

    /**
     * 退款汇总跨表对账补偿：无入参，内部扫一批 {@code PAY_REFUND_DETAIL}（唯一账本）与
     * {@code PAY_TXN_DETAIL} 的 {@code REFUND_AMOUNT} / {@code REFUND_STATUS} 两列汇总不一致的
     * 原支付订单，逐单重算汇总。调用方可反复调用直到 {@code scanned} 为 0。
     *
     * <p><b>触发方是 web-admin 的 Quartz {@code sys_job}</b>，本模块 NEVER 自带 {@code @Scheduled}
     * （全模块一个都没有，见 AGENTS.md §2.2.1；排查「退款汇总对账有没有跑」MUST 查 {@code SYS_JOB_LOG}，
     * NEVER 在本模块里找 {@code @Scheduled}）。</p>
     *
     * <p><b>它与 {@code /internal/payment/compensateRefundQuery} 是两件不同的事，NEVER 合并成一个端点</b>：
     * 那个把停在 {@code PROCESSING} 的退款推到终态、<b>会出网</b>调支付中心 §3.2 refundQuery；
     * 本端点<b>不出网</b>，只做本地两表的汇总重算。合并后既没法分别调频（一个要贴着退款时效跑、
     * 一个是日终级别的对账），出网那半边一挂也会连带把纯本地的这半边一起拖停。</p>
     *
     * <p>返回的 {@code skipped} 里混着两种单，<b>看到非 0 不等于「下一轮会自己好」</b>：
     * 一种是重算影响 0 行（下一轮重扫即可），另一种是「明细已 {@code SUCCESS} 但原支付订单不存在」，
     * 本任务<b>永远不会自愈</b>、只打 WARN 等人工。区分 MUST 看日志措辞，不要只看计数。</p>
     *
     * <p>⚠️ 与 {@code /internal/**} 下其余接口一样，本接口**没有鉴权**（模块无 spring-security、
     * 无全局拦截器，属已登记的 P0）。它会按扫表结果改 {@code PAY_TXN_DETAIL} 的退款汇总列，
     * 因此上线前 MUST 确认该端口不对外暴露；补鉴权时 MUST 与 {@code /internal/paySign/**}、
     * {@code /internal/termination/**} 一起做，NEVER 在这里自造签名逻辑。</p>
     */
    @PostMapping("/compensateRefundSummary")
    public CompensateNotifyRespDTO compensateRefundSummary() {
        log.info("收到退款汇总跨表对账补偿请求");
        return refundDomainService.compensateRefundSummary();
    }
}
