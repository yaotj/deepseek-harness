package com.chinasofti.huateng.recon.service;

import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import com.chinasofti.huateng.model.recon.ReconFileTypeEnum;
import com.chinasofti.huateng.recon.mapper.ReconBatchMapper;
import com.chinasofti.huateng.recon.model.BatchView;
import com.chinasofti.huateng.recon.model.CreateBatchRequest;
import com.chinasofti.huateng.recon.model.ReconBatchStatus;
import com.chinasofti.huateng.recon.model.ReconFileStatus;
import com.chinasofti.huateng.recon.model.ReconFileType;
import com.chinasofti.huateng.recon.model.ReconFileView;
import com.chinasofti.huateng.recon.model.ReconSourceStatus;
import com.chinasofti.huateng.recon.model.SourceProgress;
import com.chinasofti.huateng.recon.storage.ReconOrchestrationProperties;
import com.chinasofti.huateng.recon.storage.ReconOrchestrationProperties.SourceExpectation;
import com.chinasofti.huateng.rpc.recon.ReconExportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日终对账的编排：建批次、下发抽取、推进收齐、生成与投递。
 *
 * <p><b>本类没有任何 {@code @Scheduled}，触发全部来自外部。</b>用户 2026-09-11 要求
 * 「不使用 EnableScheduling，改用 web-admin 调用，改为每日执行一次，由 web-admin 控制频率」，
 * 因此唯一的日常入口是 {@link #runDailyBatch()}，由 web-admin 的 {@code sys_job}
 * （{@code reconQuartzTask.runDailyBatch()}）经 {@code POST /internal/recon/daily/run} 调进来。
 * {@link #dispatchDailyBatch()} 与 {@link #advance()} 降级为可单独调用的步骤，供人工干预与本方法复用。</p>
 *
 * <p>本类**绝不能加 {@code @Transactional}**：内部既有 RPC（下发抽取指令）又有大文件 IO
 * （合并生成、FTP 投递），被事务包住会让行级锁的持有时长等于对端响应时长与文件大小，
 * 并在 Druid 回收连接后把整个事务连同状态记录一起丢弃。每一步都靠单条 SQL 自动提交落状态。</p>
 *
 * <p>两把进程内 {@link AtomicBoolean}：{@code running} 守整批运行（前台「执行一次」与 cron 重叠时
 * 第二次直接拒绝），{@code advancing} 守单轮推进。**都只在进程内有效**，因此
 * <b>recon-server MUST 单副本</b>。</p>
 */
@Service
public class ReconOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ReconOrchestrationService.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReconOrchestrationProperties properties;

    private final ReconBatchMapper batchMapper;

    private final ReconBatchService batchService;

    private final ReconSourceService sourceService;

    private final ReconFileGenerationService fileGenerationService;

    private final ReconFileTransferService fileTransferService;

    private final ReconExportClient exportClient;

    private final AtomicBoolean advancing = new AtomicBoolean(false);

    private final AtomicBoolean running = new AtomicBoolean(false);

    public ReconOrchestrationService(ReconOrchestrationProperties properties,
                                    ReconBatchMapper batchMapper,
                                    ReconBatchService batchService,
                                    ReconSourceService sourceService,
                                    ReconFileGenerationService fileGenerationService,
                                    ReconFileTransferService fileTransferService,
                                    ReconExportClient exportClient) {
        this.properties = properties;
        this.batchMapper = batchMapper;
        this.batchService = batchService;
        this.sourceService = sourceService;
        this.fileGenerationService = fileGenerationService;
        this.fileTransferService = fileTransferService;
        this.exportClient = exportClient;
    }
    /**
     * 跑完一次完整的日终对账：建批次 → 下发抽取 → 轮询推进到终态 → 生成 → 投递。
     *
     * <p><b>这是 web-admin 每日调用的唯一入口</b>（`reconQuartzTask.runDailyBatch()`）。
     * 之所以要在这里轮询而不是「下发完就返回」：源服务的抽取是**异步**的（受理即返回，之后才回推分片），
     * 不等收齐就没有任何东西会来推进批次——本模块已没有 `@Scheduled` 兜底了。</p>
     *
     * <p><b>同步跑完再返回，是为了让 {@code sys_job_log} 反映真实成败</b>
     * （{@code docs/architecture/web-server.md} §7.1：不抛异常 Quartz 一律记「成功」）。
     * 失败或超时都抛 {@link IllegalStateException}，由 Controller 转成非 {@code 0000} 的 retCode。</p>
     *
     * <p>幂等：建批次与登记期望都是 {@code MERGE INTO}，批次已 SUCCESS 时 {@link #advanceBatch} 直接返回，
     * 因此前台「执行一次」与 cron 重叠、或当天重复触发都安全。并发用 {@code running} 拦住：
     * 上一次还在跑就直接拒绝，**NEVER 改成排队等待**——那会让 Quartz worker 越积越多。</p>
     *
     * @return 收口后的批次视图
     * @throws IllegalStateException 上一次运行未结束、总开关关闭、批次未在超时内到达终态，或推进过程中失败
     */
    public BatchView runDailyBatch() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("对账编排总开关已关闭: recon.orchestration.enabled=false");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("上一次日终对账运行尚未结束，本次拒绝");
        }
        long startNanos = System.nanoTime();
        try {
            String batchId = dispatchDailyBatch();
            long timeout = properties.getRunTimeoutMillis();
            long interval = Math.max(1000L, properties.getAdvanceDelayMillis());
            int round = 0;
            while (true) {
                BatchView batch = advanceBatch(batchId);
                round++;
                if (batch.status() == ReconBatchStatus.SUCCESS) {
                    log.info("日终对账运行完成 batchId={}, rounds={}, elapsedMs={}",
                            batchId, round, elapsedMillis(startNanos));
                    return batch;
                }
                if (elapsedMillis(startNanos) + interval > timeout) {
                    throw new IllegalStateException("日终对账在 " + timeout + "ms 内未收口: batchId=" + batchId
                            + ", status=" + batch.status() + ", rounds=" + round
                            + "（批次留在非终态，下一次运行会重入，不丢数据）");
                }
                log.info("日终对账尚未收口，等待后再推进 batchId={}, status={}, round={}", batchId, batch.status(), round);
                sleep(interval);
            }
        } finally {
            running.set(false);
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 轮询间隔的等待。
     *
     * <p>用 {@link Thread#sleep} 而不是 {@code synchronized}+{@code wait}：全服务默认虚拟线程
     * （AGENTS.md §5.2），{@code Thread.sleep} 在虚拟线程上会让出载体线程、不 pin。</p>
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("日终对账等待被中断", ex);
        }
    }

    /**
     * 建当日账期批次、登记期望清单并逐个下发抽取指令。
     *
     * <p>账期与窗口按甲方《ACC与ITP之间的文件》§一「对账文件」推算：</p>
     * <pre>
     * businessDate = 今天 - windowOffsetDays        默认 T-2
     * windowStart  = businessDate 当天 + windowStartTime      默认 02:00:00
     * windowEnd    = businessDate + 1 天 + windowStartTime
     * 文件名后缀   = businessDate                   即 T-2 日
     * </pre>
     *
     * <p>甲方例子（作为自校验依据）：8 月 20 号 2 点生成单边交易文件，文件名为 ITP.EXP.20190818，
     * 统计区间是 T-2 日 2 点 ~ T-1 日 2 点。代入本公式：今天 = 2019-08-20、windowOffsetDays = 2
     * ⇒ businessDate = 2019-08-18 ⇒ 文件名后缀 20190818（与甲方一致）、
     * 窗口 = [20190818 020000, 20190819 020000)（即 T-2 日 2 点到 T-1 日 2 点，与甲方一致）。</p>
     *
     * <p>建批次与登记期望都幂等，因此重复触发（人工重跑、当天多次执行）都安全。
     * <b>「已 SUCCESS 就直接返回」这一条 NEVER 删</b>：{@code create} 用的是
     * {@code insertIfAbsent}，对已存在的批次不会改任何列，但紧随其后的
     * {@code transitionIfNeeded(EXPORTING)} 会走 {@link ReconBatchService#transition}，
     * 而白名单里 {@code SUCCESS} 是终态、一律拒绝流出，于是同一账期第二次触发直接抛
     * 「非法的对账批次状态变更: SUCCESS -&gt; EXPORTING」、接口返 9999。cron 每天算出的
     * batchId 不同所以碰不到，但**前台「执行一次」在同一天点第二下必然踩**
     * （2026-09-11 读码发现，此前 Javadoc 声称「重复触发安全」只在 {@link #advanceBatch}
     * 那一层成立，本方法这一层并没有兑现）。</p>
     *
     * @return 本次账期的批次号，形如 {@code RECON20260910}
     * @throws IllegalStateException 建批次或登记期望失败（原先是记日志后 return，现在 MUST 抛，
     *                               否则 {@link #runDailyBatch()} 会拿着一个不存在的批次去轮询到超时）
     */
    public String dispatchDailyBatch() {
        LocalDate businessDate = LocalDate.now().minusDays(properties.getWindowOffsetDays());
        String businessDateText = businessDate.format(DATE);
        String windowStart = businessDateText + properties.getWindowStartTime();
        String windowEnd = businessDate.plusDays(1).format(DATE) + properties.getWindowStartTime();
        String batchId = "RECON" + businessDateText;
        try {
            CreateBatchRequest request = new CreateBatchRequest();
            request.setBatchId(batchId);
            request.setBusinessDate(businessDateText);
            request.setWindowStart(windowStart);
            request.setWindowEnd(windowEnd);
            BatchView batch = batchService.create(request);
            if (batch.status() == ReconBatchStatus.SUCCESS) {
                log.info("对账批次本账期已收口，跳过重复下发 batchId={}, status=SUCCESS", batchId);
                return batchId;
            }
            sourceService.registerExpectations(batchId, properties.getSources());
            batchService.transitionIfNeeded(batchId, ReconBatchStatus.EXPORTING);
            log.info("对账批次已建立并进入下发 batchId={}, window=[{}, {})", batchId, windowStart, windowEnd);
        } catch (RuntimeException ex) {
            log.error("对账批次建立失败 batchId={}, msg={}", batchId, ex.getMessage(), ex);
            throw new IllegalStateException("对账批次建立失败: " + batchId + ", " + ex.getMessage(), ex);
        }
        for (SourceExpectation expectation : properties.getSources()) {
            dispatchSource(batchId, businessDateText, windowStart, windowEnd, expectation, null);
        }
        return batchId;
    }

    /**
     * 向一个源下发抽取指令。
     *
     * @param onlyType 非空时只重下发该文件类型（补偿单个 {@code (来源, 文件类型)}），为空时下发该源的全部类型
     */
    private void dispatchSource(String batchId, String businessDate, String windowStart, String windowEnd,
                                SourceExpectation expectation, ReconFileType onlyType) {
        List<ReconFileType> targets = new ArrayList<>();
        for (String name : expectation.getFileTypes()) {
            ReconFileType type = ReconFileType.valueOf(name.trim().toUpperCase());
            if (onlyType == null || onlyType == type) targets.add(type);
        }
        if (targets.isEmpty()) return;

        ReconExportReqDTO request = new ReconExportReqDTO();
        request.setBatchId(batchId);
        request.setBusinessDate(businessDate);
        request.setWindowStart(windowStart);
        request.setWindowEnd(windowEnd);
        request.setFileTypes(targets.stream().map(ReconFileType::name).toList());
        try {
            request.validate();
        } catch (RuntimeException ex) {
            targets.forEach(type -> sourceService.markFailed(batchId, expectation.getName(), type,
                    "抽取指令参数非法: " + ex.getMessage()));
            log.error("对账抽取指令参数非法 batchId={}, source={}, msg={}", batchId, expectation.getName(), ex.getMessage());
            return;
        }

        // MUST 先置 EXPORTING 再发 HTTP：源侧空结果集的抽取可能在响应到达前就回调 complete，
        // 反过来写会把 COMPLETED 覆盖成 EXPORTING（2026-09-11 实测，见 markExporting 的注释）。
        targets.forEach(type -> sourceService.markExporting(batchId, expectation.getName(), type));
        ReconExportRespDTO response = exportClient.dispatch(expectation.getUrl(), request);
        if (response.isAccepted()) {
            log.info("对账抽取指令已受理 batchId={}, source={}, fileTypes={}", batchId, expectation.getName(), targets);
        } else {
            targets.forEach(type -> sourceService.markFailed(batchId, expectation.getName(), type, response.getMessage()));
            log.error("对账抽取指令被回绝 batchId={}, source={}, fileTypes={}, msg={}",
                    batchId, expectation.getName(), targets, response.getMessage());
        }
    }

    /**
     * 扫描非终态批次并逐个推进。
     *
     * <p>供人工干预接口调用（{@link #runDailyBatch()} 走的是按 batchId 的 {@link #advanceBatch}）。
     * 用 {@link AtomicBoolean} 做进程内串行化：上一轮还在跑就直接跳过本轮。</p>
     */
    public void advance() {
        if (!properties.isEnabled()) return;
        if (!advancing.compareAndSet(false, true)) {
            log.info("上一轮对账推进尚未结束，跳过本轮");
            return;
        }
        try {
            for (BatchView batch : batchMapper.selectUnfinished()) {
                advanceBatch(batch.batchId());
            }
        } finally {
            advancing.set(false);
        }
    }

    /**
     * 推进单个批次，供定时任务与人工干预接口共用。
     *
     * <p>任一步抛异常都把批次置 FAILED 并记原因，下一轮从 FAILED 重入
     * （白名单允许 FAILED -&gt; EXPORTING / GENERATING / UPLOADING）。</p>
     *
     * @param batchId 批次标识
     * @return 推进后的批次视图
     */
    public BatchView advanceBatch(String batchId) {
        BatchView batch = batchService.get(batchId);
        if (batch == null) throw new IllegalArgumentException("对账批次不存在: " + batchId);
        if (batch.status() == ReconBatchStatus.SUCCESS) return batch;
        try {
            retryFailedSources(batch);
            List<SourceProgress> progresses = sourceService.list(batchId);
            if (progresses.stream().anyMatch(p -> p.status() == ReconSourceStatus.MISMATCH)) {
                log.error("对账批次存在收齐校验不一致的来源，停止自动推进，MUST 人工介入 batchId={}", batchId);
                return batchService.transitionIfNeeded(batchId, ReconBatchStatus.FAILED);
            }
            boolean all = sourceService.allCompleted(batchId);
            if (!all) {
                if (sourceService.anyCompleted(batchId)) {
                    return batchService.transitionIfNeeded(batchId, ReconBatchStatus.PARTIAL);
                }
                return batchService.get(batchId);
            }
            batchService.transitionIfNeeded(batchId, ReconBatchStatus.ALL_SOURCE_COMPLETED);
            generateAll(batchId, progresses);
            uploadAll(batchId, progresses);
            return batchService.transitionIfNeeded(batchId, ReconBatchStatus.SUCCESS);
        } catch (Exception ex) {
            log.error("对账批次推进失败 batchId={}, msg={}", batchId, ex.getMessage(), ex);
            return batchService.transitionIfNeeded(batchId, ReconBatchStatus.FAILED);
        }
    }

    /**
     * 对 FAILED 且未超过 maxRetry 的来源重新下发抽取指令。
     *
     * <p>本方法内含 RPC，因此**必须在事务外**执行；调用链上没有任何 {@code @Transactional}。</p>
     *
     * <p>{@code retryCount} 是装箱的 {@link Integer}（MyBatis 构造器映射要求），此处 MUST 先判空
     * 兜成 0 再与 {@code maxRetry} 比较：直接 {@code progress.retryCount() >= ...} 会在列为 NULL 时
     * 抛 NPE，而 NPE 会被 {@code advanceBatch} 的 catch 吞成「批次推进失败」，掩盖真实原因。</p>
     */
    private void retryFailedSources(BatchView batch) {
        boolean retried = false;
        for (SourceProgress progress : sourceService.list(batch.batchId())) {
            if (progress.status() != ReconSourceStatus.FAILED) continue;
            int retryCount = Objects.requireNonNullElse(progress.retryCount(), 0);
            if (retryCount >= properties.getMaxRetry()) {
                log.error("对账来源重试次数已达上限，停止自动重下发 batchId={}, source={}, fileType={}, retryCount={}",
                        batch.batchId(), progress.source(), progress.fileType(), retryCount);
                continue;
            }
            SourceExpectation expectation = findExpectation(progress.source());
            if (expectation == null) {
                log.error("对账来源不在期望清单中，无法重下发 batchId={}, source={}", batch.batchId(), progress.source());
                continue;
            }
            sourceService.increaseRetry(batch.batchId(), progress.source(), progress.fileType());
            dispatchSource(batch.batchId(), batch.businessDate(), batch.windowStart(), batch.windowEnd(),
                    expectation, progress.fileType());
            retried = true;
        }
        if (retried && batchService.get(batch.batchId()).status() == ReconBatchStatus.FAILED) {
            batchService.transitionIfNeeded(batch.batchId(), ReconBatchStatus.EXPORTING);
        }
    }

    private SourceExpectation findExpectation(String source) {
        for (SourceExpectation expectation : properties.getSources()) {
            if (source.equals(expectation.getName())) return expectation;
        }
        return null;
    }

    /** 逐个生成最终文件；任一失败即抛异常，由上层置批次 FAILED。 */
    private void generateAll(String batchId, List<SourceProgress> progresses) throws Exception {
        for (ReconFileType fileType : requiredFileTypes(progresses)) {
            ReconFileView view = fileGenerationService.generate(batchId, fileType);
            log.info("对账文件已生成 batchId={}, fileType={}, records={}, amount={}, bytes={}",
                    batchId, fileType, view.recordCount(), view.amountTotal(), view.byteCount());
        }
    }

    /** 先把批次推到 UPLOADING，再逐个投递；全部 UPLOADED 才算通过。 */
    private void uploadAll(String batchId, List<SourceProgress> progresses) throws Exception {
        batchService.transitionIfNeeded(batchId, ReconBatchStatus.UPLOADING);
        for (ReconFileType fileType : requiredFileTypes(progresses)) {
            ReconFileView view = fileTransferService.upload(batchId, fileType);
            if (!ReconFileStatus.UPLOADED.name().equals(view.status())) {
                throw new IllegalStateException("对账文件投递未完成: " + fileType + " status=" + view.status());
            }
            log.info("对账文件已投递 batchId={}, fileType={}, remotePath={}", batchId, fileType, view.remotePath());
        }
    }

    /**
     * 需要产出的文件类型 = 期望清单里所有 fileTypes 的并集。
     *
     * <p>按 {@link ReconFileTypeEnum} 的声明顺序输出，先明细后汇总没有强依赖，但顺序固定便于对日志。</p>
     */
    private Set<ReconFileType> requiredFileTypes(List<SourceProgress> progresses) {
        Set<ReconFileType> types = new LinkedHashSet<>();
        for (ReconFileTypeEnum candidate : ReconFileTypeEnum.values()) {
            ReconFileType type = ReconFileType.valueOf(candidate.name());
            if (progresses.stream().anyMatch(p -> p.fileType() == type)) types.add(type);
        }
        return types;
    }
}
