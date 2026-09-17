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
     * <p>护栏：并发只靠进程内 {@link AtomicBoolean}、没有数据库锁，因此 recon-server 与
     * web-admin 都 MUST 单副本。</p>
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

    /** 轮询间隔的等待。 */
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
     * @return 本次账期的批次号，形如 {@code RECON20260910}
     * @throws IllegalStateException 建批次或登记期望失败
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

    /** 扫描非终态批次并逐个推进，供人工干预接口调用。 */
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
     * 推进单个批次，供人工干预接口与整批运行共用。
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

    /** 对 FAILED 且未超过 maxRetry 的来源重新下发抽取指令；本方法内含 RPC，MUST 在事务外执行。 */
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

    /** 需要产出的文件类型 = 期望清单里所有 fileTypes 的并集，按 {@link ReconFileTypeEnum} 声明顺序输出。 */
    private Set<ReconFileType> requiredFileTypes(List<SourceProgress> progresses) {
        Set<ReconFileType> types = new LinkedHashSet<>();
        for (ReconFileTypeEnum candidate : ReconFileTypeEnum.values()) {
            ReconFileType type = ReconFileType.valueOf(candidate.name());
            if (progresses.stream().anyMatch(p -> p.fileType() == type)) types.add(type);
        }
        return types;
    }
}
