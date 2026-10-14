package com.chinasofti.huateng.recon.thirdparty;

import com.chinasofti.huateng.recon.mapper.ReconThirdBillMapper;
import com.chinasofti.huateng.recon.storage.ThirdPartyReconProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 第三方支付对账编排：取渠道账单 → 入库 → 抽 ITP 台账 → 比对 → 生成 TXNDIFF 并投递。
 *
 * <p>调度归属（recon-thirdparty.md §五 项9 默认假设）：独立流程，不并入现有四类文件日终批(sys_job 225)。
 * 通过内部端点 /internal/recon/thirdparty/run 由 web-admin sys_job 触发；也可由本类 @Scheduled 触发（默认关闭，避免双跑）。</p>
 */
@Service
public class ThirdPartyReconScheduler {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyReconScheduler.class);

    private final ThirdPartyReconProperties properties;
    private final ThirdPartyBillIngestService ingestService;
    private final ReconThirdBillMapper billMapper;
    private final ItpTxnExtractor itpExtractor;
    private final TxnDiffReconService reconService;
    private final TxnDiffFileGenerator fileGenerator;

    public ThirdPartyReconScheduler(ThirdPartyReconProperties properties,
                                    ThirdPartyBillIngestService ingestService,
                                    ReconThirdBillMapper billMapper,
                                    ItpTxnExtractor itpExtractor,
                                    TxnDiffReconService reconService,
                                    TxnDiffFileGenerator fileGenerator) {
        this.properties = properties;
        this.ingestService = ingestService;
        this.billMapper = billMapper;
        this.itpExtractor = itpExtractor;
        this.reconService = reconService;
        this.fileGenerator = fileGenerator;
    }

    /** 定时触发入口（默认关闭，由 sys_job 调内部端点为主）。 */
    @Scheduled(cron = "${recon.thirdparty.scheduler.cron:0 30 3 * * ?}")
    public void scheduledRun() {
        if (!properties.isEnabled() || !properties.isSchedulerEnabled()) {
            log.debug("第三方对账定时任务未启用，跳过");
            return;
        }
        try {
            runMatchedFiles();
        } catch (Exception ex) {
            log.error("第三方对账定时任务执行失败", ex);
        }
    }

    /** 处理本地目录中所有匹配模式的账单文件（生产主入口之一）。 */
    public List<String> runMatchedFiles() throws IOException {
        if (!properties.isEnabled()) throw new IllegalStateException("第三方对账未启用");
        Path dir = Path.of(properties.getBillLocalDir());
        if (!Files.isDirectory(dir)) {
            log.warn("渠道账单目录不存在，跳过 billLocalDir={}", dir);
            return List.of();
        }
        PathMatcher matcher = dir.getFileSystem().getPathMatcher("glob:" + properties.getBillFilePattern());
        List<Path> files = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && matcher.matches(p.getFileName())) files.add(p);
            }
        }
        if (files.isEmpty()) {
            log.warn("渠道账单目录无匹配文件 billLocalDir={} pattern={}", dir, properties.getBillFilePattern());
            return List.of();
        }
        List<String> produced = new ArrayList<>();
        for (Path file : files) {
            produced.addAll(runFile(file));
        }
        return produced;
    }

    /** 处理单个账单文件。 */
    public List<String> runFile(Path csvFile) throws IOException {
        List<ThirdPartyBill> parsed = ingestService.parseOnly(csvFile);
        Set<String> billDates = new LinkedHashSet<>();
        for (ThirdPartyBill b : parsed) {
            if (b.getBillDate() != null && !b.getBillDate().isBlank()) billDates.add(b.getBillDate());
        }
        ingestService.ingest(parsed);
        List<String> produced = new ArrayList<>();
        for (String billDate : billDates) {
            produced.add(runForDate(billDate));
        }
        return produced;
    }

    /** 对已入库的某自然日账单执行比对并生成 TXNDIFF。 */
    public String runForDate(String billDate) throws IOException {
        if (!properties.isEnabled()) throw new IllegalStateException("第三方对账未启用");
        if (billDate == null || billDate.isBlank()) throw new IllegalArgumentException("账单日不能为空");
        String txnDate = billDate.replace("-", "");
        List<ThirdPartyBill> bills = billMapper.selectByBillDate(billDate);
        List<ItpTxnRecord> itpRecords = itpExtractor.extract(txnDate);
        List<TxnDiffRecord> diffs = reconService.reconcile(billDate, txnDate, bills, itpRecords);
        return fileGenerator.generate(billDate, diffs);
    }
}
