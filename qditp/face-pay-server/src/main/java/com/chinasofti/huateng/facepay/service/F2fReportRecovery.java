package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出票上报的后续动作补偿。<b>这是 {@code F2F_RESULT_REPORT.PROCESSED} 的唯一消费方</b>——
 * 没有它，那一列恒为 {@code '0'}、而「落上报之后失败」的单子永远不会有人补。
 *
 * <h2>为什么必须有这个任务</h2>
 * <p>接收链路（{@link F2fTicketIssueService#receiveTakeTicketResult}）是
 * 「落票 → <b>落上报（幂等锚点）</b> → 推进订单 → 差额退款 → 入队通知」。
 * 上报行一旦提交，设备重传就会撞 {@code UK_F2F_REPORT_IDEM} 并<b>直接回 {@code 0000}</b>，
 * 因此后三步中任何一步失败都不会再被重做：订单永久卡 {@code PAID}、
 * 出票故障的差额退款永不发起、APP 永不收到通知，且线上只留一行日志。
 * 表设计早就把 {@code PROCESSED} 留成了出口（列注释写着「后续动作由扫表驱动」），
 * 但 1.0.45 之前 {@code selectPendingReports} / {@code markProcessed} <b>零调用方</b>。
 *
 * <h2>三条不变量，改本类前逐条对</h2>
 * <ol>
 *   <li><b>先重放、成功了才 {@code markProcessed}。</b>顺序颠倒会把「标记成功但动作没做」
 *       变成新的静默丢失 —— 而这正是本任务要修的那类缺陷。</li>
 *   <li><b>{@code markProcessed} 返回 0 就当别人已处理，NEVER 记 ERROR。</b>
 *       它的 WHERE 带 {@code PROCESSED='0'}，0 行是并发保护生效、不是故障。</li>
 *   <li><b>单条异常只计数、不中断整批</b>（与 {@code F2fNotifyDeliverer} 同款）：
 *       一条坏数据不该让同批其余单子等下一轮。异常行保持 {@code '0'}，下轮自然重来。</li>
 * </ol>
 *
 * <p><b>多副本</b>：无分布式锁，与 {@code F2fNotifyJob} / {@code F2fOrderExpireJob} 取舍一致 ——
 * 两个副本可能同时捞到同一行，但重放的三步都幂等、且 {@code markProcessed} 只有一个能拿到 1 行。
 * face-pay-server 本来就 <b>MUST 单副本</b>。</p>
 */
@Service
public class F2fReportRecovery {

    /**
     * 本任务只重放出票上报两类。
     *
     * <p>{@code BOM_BIZ_RESULT} / {@code TOPUP_OK} 等类型的后续动作在各自链路里内联完成，
     * <b>NEVER 往这个列表里加它们</b>——那等于让本任务去重放它没有实现的语义。</p>
     */
    private static final List<String> RESUMABLE_TYPES = List.of(
            F2fTicketIssueService.REPORT_TAKE_TICKET_OK,
            F2fTicketIssueService.REPORT_TAKE_TICKET_FAIL);

    private static final Logger log = LoggerFactory.getLogger(F2fReportRecovery.class);

    private final F2fResultReportMapper reportMapper;

    private final F2fTicketIssueService ticketIssueService;

    public F2fReportRecovery(F2fResultReportMapper reportMapper, F2fTicketIssueService ticketIssueService) {
        this.reportMapper = reportMapper;
        this.ticketIssueService = ticketIssueService;
    }

    /**
     * 一轮补偿的计数。{@code due} 是本轮捞到的条数，{@code resumed + skipped + errored} 应等于它。
     *
     * @param resumed 重放成功并已置 {@code PROCESSED='1'}
     * @param skipped 订单查不到 / 张数缺失 / 类型不属本任务，<b>仍保持 {@code '0'} 等人工</b>
     * @param errored 重放过程抛异常，状态未推进，下一轮仍会捞到
     */
    public record RecoverStat(int due, int resumed, int skipped, int errored) {

        static RecoverStat empty() {
            return new RecoverStat(0, 0, 0, 0);
        }
    }

    /**
     * 重放已过静默期的未处理出票上报。
     *
     * @param limit        单轮最大条数
     * @param staleSeconds 静默期秒数，只捞 {@code RECEIVE_TMS} 早于「当前时刻 - 该值」的行；
     *                     <b>MUST 大于一次出票上报请求的最长耗时</b>，否则会与首报并发
     */
    public RecoverStat resumeDue(int limit, long staleSeconds) {
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(staleSeconds);
        List<F2fResultReport> reports;
        try {
            reports = reportMapper.selectPendingReports(limit, RESUMABLE_TYPES, staleBefore);
        } catch (RuntimeException e) {
            log.error("上报补偿扫表失败, limit={}, staleBefore={}", limit, staleBefore, e);
            return RecoverStat.empty();
        }
        if (reports.isEmpty()) {
            return RecoverStat.empty();
        }
        int resumed = 0;
        int skipped = 0;
        int errored = 0;
        for (F2fResultReport report : reports) {
            try {
                if (!ticketIssueService.resumeFromReport(report)) {
                    skipped++;
                    continue;
                }
                if (reportMapper.markProcessed(report.getId()) == 0) {
                    log.info("上报补偿 该条已被其他线程标记, reportId={}", report.getId());
                }
                resumed++;
            } catch (RuntimeException e) {
                errored++;
                log.error("上报补偿处理异常, reportId={}, reportType={}, orderNo={}",
                        report.getId(), report.getReportType(), report.getOrderNo(), e);
            }
        }
        log.info("上报补偿完成, 到期={}, 已重放={}, 跳过={}, 异常={}",
                reports.size(), resumed, skipped, errored);
        return new RecoverStat(reports.size(), resumed, skipped, errored);
    }
}
