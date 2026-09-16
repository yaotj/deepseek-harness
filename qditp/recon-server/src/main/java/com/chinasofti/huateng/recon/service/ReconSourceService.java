package com.chinasofti.huateng.recon.service;

import com.chinasofti.huateng.model.recon.ReconSourceCompleteReqDTO;
import com.chinasofti.huateng.recon.mapper.ReconPartMapper;
import com.chinasofti.huateng.recon.mapper.ReconSourceMapper;
import com.chinasofti.huateng.recon.model.PartTotals;
import com.chinasofti.huateng.recon.model.ReconFileType;
import com.chinasofti.huateng.recon.model.ReconSourceStatus;
import com.chinasofti.huateng.recon.model.SourceProgress;
import com.chinasofti.huateng.recon.storage.ReconOrchestrationProperties;
import com.chinasofti.huateng.recon.storage.ReconOrchestrationProperties.SourceExpectation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * {@code RECON_BATCH_SOURCE} 的进度维护与**收齐校验**。
 *
 * <p>收齐判定只认三项总账全等：落库分片数 / 记录数 / 金额合计逐一等于源声明的
 * {@code totalParts / totalRecords / totalAmount}。任一不等即置 MISMATCH，
 * NEVER 放行——分片少一片，最终文件就少几十万行，而纯文本文件本身看不出缺失。</p>
 */
@Service
public class ReconSourceService {

    private static final Logger log = LoggerFactory.getLogger(ReconSourceService.class);

    private final ReconSourceMapper sourceMapper;

    private final ReconPartMapper partMapper;

    private final ReconOrchestrationProperties orchestration;

    public ReconSourceService(ReconSourceMapper sourceMapper,
                              ReconPartMapper partMapper,
                              ReconOrchestrationProperties orchestration) {
        this.sourceMapper = sourceMapper;
        this.partMapper = partMapper;
        this.orchestration = orchestration;
    }

    /**
     * 按期望清单登记本批次需要收齐的每个 {@code (来源, 文件类型)}，按主键幂等。
     *
     * @param batchId 批次标识
     * @param sources 期望清单
     */
    public void registerExpectations(String batchId, List<SourceExpectation> sources) {
        if (sources == null || sources.isEmpty()) {
            log.warn("对账期望清单为空，批次 {} 无源可登记", batchId);
            return;
        }
        for (SourceExpectation expectation : sources) {
            for (String fileType : expectation.getFileTypes()) {
                ReconFileType type = ReconFileType.valueOf(fileType.trim().toUpperCase());
                sourceMapper.insertIfAbsent(batchId, expectation.getName(), type.name(),
                        ReconSourceStatus.PENDING.name());
            }
        }
    }
    /**
     * 指令已被源受理，置 EXPORTING 并清空上一轮的失败原因。
     *
     * <p>走的是 {@code updateStatusIfNotTerminal}：源侧的空结果集抽取可能比编排线程更早写完
     * COMPLETED，无条件覆盖会把它打回 EXPORTING、导致批次永不收齐（2026-09-11 实测复现，
     * 见该 mapper 方法的 Javadoc）。<b>NEVER 换回无条件的 updateStatus。</b></p>
     */
    public void markExporting(String batchId, String source, ReconFileType fileType) {
        sourceMapper.updateStatusIfNotTerminal(batchId, source, fileType.name(),
                ReconSourceStatus.EXPORTING.name(), null);
    }

    /** 指令被回绝或源声明失败，置 FAILED 并记原因，等待下一轮重下发。 */
    public void markFailed(String batchId, String source, ReconFileType fileType, String reason) {
        sourceMapper.updateStatus(batchId, source, fileType.name(), ReconSourceStatus.FAILED.name(), truncate(reason));
    }

    /** 重下发前把重试次数 +1，超过 maxRetry 后编排器不再自动重试。 */
    public void increaseRetry(String batchId, String source, ReconFileType fileType) {
        sourceMapper.increaseRetry(batchId, source, fileType.name());
    }

    public List<SourceProgress> list(String batchId) {
        return sourceMapper.selectByBatch(batchId);
    }

    /**
     * 处理源服务的完成声明，做三项总账比对后落终态。
     *
     * <p>比对项：分片数 / 记录数 / 金额合计。三项全等才置 COMPLETED；任一不等置 MISMATCH，
     * 把差异写进 {@code FAIL_REASON} 并 {@code log.error} 告警。</p>
     *
     * @param batchId  批次标识
     * @param source   来源标识
     * @param fileType 文件类型
     * @param request  源声明
     * @return 处理后的进度
     */
    public SourceProgress declare(String batchId, String source, ReconFileType fileType,
                                  ReconSourceCompleteReqDTO request) {
        SourceProgress current = sourceMapper.selectByKey(batchId, source, fileType.name());
        if (current == null) {
            throw new IllegalArgumentException("未登记的对账来源: " + batchId + "/" + source + "/" + fileType);
        }
        if (request.getFailReason() != null && !request.getFailReason().isBlank()) {
            log.error("对账来源声明抽取失败 batchId={}, source={}, fileType={}, reason={}",
                    batchId, source, fileType, request.getFailReason());
            markFailed(batchId, source, fileType, request.getFailReason());
            return sourceMapper.selectByKey(batchId, source, fileType.name());
        }

        PartTotals totals = partMapper.selectTotals(batchId, source, fileType.name());
        int currentParts = Objects.requireNonNullElse(current.declaredParts(), 0);
        long currentRecords = Objects.requireNonNullElse(current.declaredRecords(), 0L);
        long currentAmount = Objects.requireNonNullElse(current.declaredAmount(), 0L);
        boolean sameAsDeclared = currentParts == request.getTotalParts()
                && currentRecords == request.getTotalRecords()
                && currentAmount == request.getTotalAmount();
        if (current.status() == ReconSourceStatus.COMPLETED && sameAsDeclared) {
            return current;
        }

        String difference = diff(totals, request);
        if (difference == null) {
            sourceMapper.updateDeclared(batchId, source, fileType.name(), ReconSourceStatus.COMPLETED.name(),
                    request.getTotalParts(), request.getTotalRecords(), request.getTotalAmount(), null);
        } else {
            log.error("对账来源收齐校验不通过 batchId={}, source={}, fileType={}, {}",
                    batchId, source, fileType, difference);
            sourceMapper.updateDeclared(batchId, source, fileType.name(), ReconSourceStatus.MISMATCH.name(),
                    request.getTotalParts(), request.getTotalRecords(), request.getTotalAmount(), truncate(difference));
        }
        return sourceMapper.selectByKey(batchId, source, fileType.name());
    }

    /**
     * 是否全部来源都已 COMPLETED。
     *
     * <p>除了「每行都是 COMPLETED」，还要求行数等于期望条数——否则 dispatch 那一轮若中途异常
     * 少登记了几行，剩下的几行全 COMPLETED 也会被误判成收齐。</p>
     */
    public boolean allCompleted(String batchId) {
        List<SourceProgress> progresses = sourceMapper.selectByBatch(batchId);
        int expected = expectedCount();
        if (progresses.isEmpty() || expected == 0 || progresses.size() != expected) return false;
        return progresses.stream().allMatch(p -> p.status() == ReconSourceStatus.COMPLETED);
    }

    /** 是否已有来源收口，用于把批次从 EXPORTING 推到 PARTIAL。 */
    public boolean anyCompleted(String batchId) {
        return sourceMapper.selectByBatch(batchId).stream()
                .anyMatch(p -> p.status() == ReconSourceStatus.COMPLETED);
    }

    /** 期望条数 = 期望清单里所有 {@code (来源, 文件类型)} 组合数。 */
    private int expectedCount() {
        int count = 0;
        for (SourceExpectation expectation : orchestration.getSources()) {
            count += expectation.getFileTypes().size();
        }
        return count;
    }

    /**
     * 三项逐一比对，全等返回 null，否则返回可读的差异说明。
     *
     * <p>{@link PartTotals} 的分量是装箱类型（MyBatis 构造器映射要求），因此**先判空拆成基本类型**
     * 再比：{@code Integer != int} 虽然会自动拆箱，但一旦两侧都变成装箱类型，{@code !=} 就退化成
     * 引用比较、超过 {@code Integer} 缓存范围（-128~127）的相等值会被判成不等。
     * <b>NEVER 让两个装箱类型直接用 {@code ==} / {@code !=} 比较。</b></p>
     */
    private String diff(PartTotals totals, ReconSourceCompleteReqDTO request) {
        StringBuilder builder = new StringBuilder(96);
        int parts = Objects.requireNonNullElse(totals.parts(), 0);
        long records = Objects.requireNonNullElse(totals.records(), 0L);
        long amount = Objects.requireNonNullElse(totals.amount(), 0L);
        if (parts != request.getTotalParts()) {
            builder.append("分片数 落库=").append(parts).append(" 声明=").append(request.getTotalParts());
        }
        if (records != request.getTotalRecords()) {
            if (builder.length() > 0) builder.append("; ");
            builder.append("记录数 落库=").append(records).append(" 声明=").append(request.getTotalRecords());
        }
        if (amount != request.getTotalAmount()) {
            if (builder.length() > 0) builder.append("; ");
            builder.append("金额 落库=").append(amount).append(" 声明=").append(request.getTotalAmount());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /** FAIL_REASON 列长 1024，超长截断，避免 ORA-12899 让整条声明失败。 */
    private String truncate(String reason) {
        if (reason == null) return null;
        return reason.length() <= 1024 ? reason : reason.substring(0, 1024);
    }
}
