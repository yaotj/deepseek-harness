package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;

import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** 解约域的**扫表补偿**能力：解约申请批处理、解约通知补发、通道清理补发。 */
@Service
public class TerminationCompensationService {

    private static final Logger log = LoggerFactory.getLogger(TerminationCompensationService.class);

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();
    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();

    /** PENDING 滞留多久（分钟）视为「状态回写丢了」，纳入补偿。 */
    private static final int PENDING_STALE_MINUTES = 10;
    /** 单次扫表处理的最大条数，调用方可反复调用直到 scanned 为 0。 */
    private static final int BATCH_SIZE = 200;

    /** referenceTime 入参支持的两种格式：yyyyMMdd（按当天 00:00:00 解析）与 yyyyMMddHHmmss。 */
    private static final DateTimeFormatter REFERENCE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter REFERENCE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 规定天数默认值：解约申请提交满这么多天才确认解约（业务规定 4 天）。 */
    @Value("${termination.confirm-delay-days:4}")
    private int confirmDelayDays;

    /** 通知重试上限，超过后该记录不再被补偿扫到，只能人工介入。 */
    @Value("${app.notify.max-retry-count:10}")
    private int maxNotifyRetryCount;

    /** 通道清理的重试上限（ADR-D48）。**与通知的上限刻意分开配**：通知失败只是 APP 少收一条消息。 */
    @Value("${termination.channel-sync.max-retry-count:10}")
    private int maxChannelSyncRetryCount;

    /** 通道清理的唯一投递点，与回调收口的快速路径共用，NEVER 在本类复制其三分支处置。 */
    private final ChannelSyncDeliverer channelSyncDeliverer;

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final AppNotifyService appNotifyService;

    private final TerminationProcessor terminationProcessor;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public TerminationCompensationService(
            ChannelSyncDeliverer channelSyncDeliverer,
            AppTerminationRequestMapper terminationRequestMapper,
            AppNotifyService appNotifyService,
            TerminationProcessor terminationProcessor) {
        this.channelSyncDeliverer = channelSyncDeliverer;
        this.terminationRequestMapper = terminationRequestMapper;
        this.appNotifyService = appNotifyService;
        this.terminationProcessor = terminationProcessor;
    }

    public ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request) {
        ProcessTerminationRespDTO response = new ProcessTerminationRespDTO();
        try {
            LocalDateTime cutoff;
            try {
                cutoff = resolveCutoff(request);
            } catch (DateTimeParseException e) {
                log.warn("解约申请批处理入参 referenceTime 格式非法, request={}", request, e);
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM,
                        "referenceTime 只接受 yyyyMMdd 或 yyyyMMddHHmmss");
                return response;
            }
            List<AppTerminationRequest> records = new ArrayList<>();
            addBatch(records, STATUS_PENDING, cutoff);
            addBatch(records, STATUS_SCANNING, cutoff);
            if (records.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            response.setScanned(records.size());
            for (AppTerminationRequest record : records) {
                try {
                    switch (terminationProcessor.processOne(record)) {
                        case TERMINATED -> response.setTerminated(response.getTerminated() + 1);
                        case CONFIRMED -> response.setConfirmed(response.getConfirmed() + 1);
                        case REJECTED -> response.setRejected(response.getRejected() + 1);
                        case EXPIRED -> response.setExpired(response.getExpired() + 1);
                        case SKIPPED -> response.setSkipped(response.getSkipped() + 1);
                    }
                } catch (Exception e) {
                    log.error("处理解约申请异常, requestSignSeq={}", record.getRequestSignSeq(), e);
                    response.setSkipped(response.getSkipped() + 1);
                }
            }
            log.info("解约申请批处理完成, cutoff={}, scanned={}, terminated={}, confirmed={}, rejected={}, expired={}, skipped={}",
                    cutoff, response.getScanned(), response.getTerminated(), response.getConfirmed(),
                    response.getRejected(), response.getExpired(), response.getSkipped());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("解约申请批处理异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 算出本次的申请时间截止点：{@code referenceTime - delayDays}。
     *
     * @throws DateTimeParseException referenceTime 既不是 yyyyMMdd 也不是 yyyyMMddHHmmss
     */
    private LocalDateTime resolveCutoff(ProcessTerminationReqDTO request) {
        if (request == null
                || (!StringUtils.hasText(request.getReferenceTime()) && request.getDelayDays() == null)) {
            return null;
        }
        LocalDateTime reference = parseReferenceTime(request.getReferenceTime());
        int delayDays = request.getDelayDays() != null ? request.getDelayDays() : confirmDelayDays;
        if (delayDays < 0) {
            log.warn("delayDays 为负数，按 0 处理, delayDays={}", delayDays);
            delayDays = 0;
        }
        return reference.minusDays(delayDays);
    }

    /** referenceTime 为空取当前时间；yyyyMMdd 按当天 00:00:00 解析。 */
    private LocalDateTime parseReferenceTime(String referenceTime) {
        if (!StringUtils.hasText(referenceTime)) {
            return LocalDateTime.now();
        }
        String trimmed = referenceTime.trim();
        if (trimmed.length() == 8) {
            return LocalDate.parse(trimmed, REFERENCE_DATE_FORMATTER).atStartOfDay();
        }
        return LocalDateTime.parse(trimmed, REFERENCE_DATE_TIME_FORMATTER);
    }

    /** 按状态取一批解约申请追加到待处理列表，cutoff 非空时只取该时刻及之前提交的申请。 */
    private void addBatch(List<AppTerminationRequest> target, String status, LocalDateTime cutoff) {
        List<AppTerminationRequest> batch = cutoff == null
                ? terminationRequestMapper.selectByStatusLimit(status, BATCH_SIZE)
                : terminationRequestMapper.selectByStatusBefore(status, cutoff, BATCH_SIZE);
        if (batch != null && !batch.isEmpty()) {
            target.addAll(batch);
        }
    }

    public CompensateNotifyRespDTO compensateTerminationNotify() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            List<AppTerminationRequest> records = terminationRequestMapper
                    .selectCompensableNotify(maxNotifyRetryCount, PENDING_STALE_MINUTES, BATCH_SIZE);
            if (records == null || records.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            response.setScanned(records.size());
            for (AppTerminationRequest record : records) {
                try {
                    terminationRequestMapper.increaseNotifyRetryCount(record.getRequestSignSeq());
                    appNotifyService.asyncRetryTerminationNotify(record);
                    response.setSubmitted(response.getSubmitted() + 1);
                } catch (Exception e) {
                    log.error("提交解约通知重发异常, requestSignSeq={}", record.getRequestSignSeq(), e);
                    response.setSkipped(response.getSkipped() + 1);
                }
            }
            log.info("解约通知补偿完成, scanned={}, submitted={}, skipped={}",
                    response.getScanned(), response.getSubmitted(), response.getSkipped());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("解约通知补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    public CompensateNotifyRespDTO compensateChannelSync() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            List<AppTerminationRequest> pending = terminationRequestMapper
                    .selectCompensableChannelSync(maxChannelSyncRetryCount, PENDING_STALE_MINUTES, BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            OutboxScan.Result scan = OutboxScan.run(pending,
                    channelSyncDeliverer::deliver,
                    row -> log.warn("通道清理本轮未成功, requestSignSeq={}", row.getRequestSignSeq()),
                    (row, e) -> log.error("单条通道清理异常，NEVER 因此中断整批, requestSignSeq={}",
                            row.getRequestSignSeq(), e));
            response.setScanned(scan.scanned());
            response.setSubmitted(scan.success());
            response.setSkipped(scan.failed());
            log.info("通道清理补偿完成, scanned={}, success={}, failed={}",
                    scan.scanned(), scan.success(), scan.failed());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("通道清理补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }
}
