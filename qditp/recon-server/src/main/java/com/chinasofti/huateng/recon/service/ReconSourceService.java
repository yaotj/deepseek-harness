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


    public void registerSingle(String batchId, String source, ReconFileType fileType) {
        sourceMapper.insertIfAbsent(batchId, source, fileType.name(), ReconSourceStatus.PENDING.name());
    }
    public void markExporting(String batchId, String source, ReconFileType fileType) {
        sourceMapper.updateStatusIfNotTerminal(batchId, source, fileType.name(),
                ReconSourceStatus.EXPORTING.name(), null);
    }

    public void markFailed(String batchId, String source, ReconFileType fileType, String reason) {
        sourceMapper.updateStatus(batchId, source, fileType.name(), ReconSourceStatus.FAILED.name(), truncate(reason));
    }

    public void increaseRetry(String batchId, String source, ReconFileType fileType) {
        sourceMapper.increaseRetry(batchId, source, fileType.name());
    }

    public List<SourceProgress> list(String batchId) {
        return sourceMapper.selectByBatch(batchId);
    }


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

    public boolean allCompleted(String batchId) {
        List<SourceProgress> progresses = sourceMapper.selectByBatch(batchId);
        int expected = expectedCount();
        if (progresses.isEmpty() || expected == 0 || progresses.size() != expected) return false;
        return progresses.stream().allMatch(p -> p.status() == ReconSourceStatus.COMPLETED);
    }

    public boolean anyCompleted(String batchId) {
        return sourceMapper.selectByBatch(batchId).stream()
                .anyMatch(p -> p.status() == ReconSourceStatus.COMPLETED);
    }

    private int expectedCount() {
        int count = 0;
        for (SourceExpectation expectation : orchestration.getSources()) {
            count += expectation.getFileTypes().size();
        }
        return count;
    }

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

    private String truncate(String reason) {
        if (reason == null) return null;
        return reason.length() <= 1024 ? reason : reason.substring(0, 1024);
    }
}
