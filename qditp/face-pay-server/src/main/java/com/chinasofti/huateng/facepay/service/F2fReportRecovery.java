package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 出票上报的后续动作补偿。 */
@Service
public class F2fReportRecovery {

    /** 本任务只重放出票上报两类。 */
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
     * 一轮补偿的计数。
     *
     * @param resumed 重放成功并已置 {@code PROCESSED='1'}
     * @param skipped 订单查不到 / 张数缺失 / 类型不属本任务，仍保持 {@code '0'} 等人工
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
