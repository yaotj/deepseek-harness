package com.chinasofti.huateng.recon.thirdparty;

import com.chinasofti.huateng.model.recon.F2fTxnLine;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import com.chinasofti.huateng.model.recon.ReconFileTypeEnum;
import com.chinasofti.huateng.recon.mapper.ReconPartMapper;
import com.chinasofti.huateng.recon.model.CreateBatchRequest;
import com.chinasofti.huateng.recon.model.PartReceipt;
import com.chinasofti.huateng.recon.model.ReconFileType;
import com.chinasofti.huateng.recon.model.ReconSourceStatus;
import com.chinasofti.huateng.recon.model.SourceProgress;
import com.chinasofti.huateng.recon.service.ReconBatchService;
import com.chinasofti.huateng.recon.service.ReconSourceService;
import com.chinasofti.huateng.recon.storage.ReconOrchestrationProperties;
import com.chinasofti.huateng.recon.storage.ReconStorageProperties;
import com.chinasofti.huateng.recon.storage.ThirdPartyReconProperties;
import com.chinasofti.huateng.rpc.recon.ReconExportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 按账期向 face-pay-server 拉取 F2F 侧 TXNDIFF 数据源分片。
 *
 * <p>为什么是「按账期拉取」而不是 recon-server 直接查 {@code F2F_ORDER/F2F_PAYMENT}：face-pay 库即将拆库、
 * recon-server 的单数据源账号没有这两张表的 SELECT 授权（recon-thirdparty.md §八）；沿用既有对账分片机制——
 * recon-server 下发抽取指令，face-pay-server 现场抽数、经 {@code ReconPartSink} 上送落盘，本类再读回。
 * 渠道账单日天然随账单变化，故每次比对都按 {@code txnDate} 起一个独立小批次（{@code RECON_F2F_{txnDate}}），
 * 不复用固定窗口的日终四大类批。</p>
 */
@Service
public class FacePayTxnShardFetcher {

    private static final Logger log = LoggerFactory.getLogger(FacePayTxnShardFetcher.class);

    private static final String SOURCE = "face-pay";
    private static final String FILE_TYPE = ReconFileTypeEnum.F2F_TXN.name();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ReconBatchService batchService;
    private final ReconSourceService sourceService;
    private final ReconPartMapper partMapper;
    private final ReconExportClient exportClient;
    private final ReconStorageProperties storageProperties;
    private final ReconOrchestrationProperties orchestration;
    private final ThirdPartyReconProperties thirdPartyProperties;

    public FacePayTxnShardFetcher(ReconBatchService batchService,
                                  ReconSourceService sourceService,
                                  ReconPartMapper partMapper,
                                  ReconExportClient exportClient,
                                  ReconStorageProperties storageProperties,
                                  ReconOrchestrationProperties orchestration,
                                  ThirdPartyReconProperties thirdPartyProperties) {
        this.batchService = batchService;
        this.sourceService = sourceService;
        this.partMapper = partMapper;
        this.exportClient = exportClient;
        this.storageProperties = storageProperties;
        this.orchestration = orchestration;
        this.thirdPartyProperties = thirdPartyProperties;
    }

    /** 拉取指定自然日(txnDate=YYYYMMDD)的 face-pay F2F 流水，转成 {@link ItpTxnRecord} 列表。 */
    public List<ItpTxnRecord> fetch(String txnDate) {
        String batchId = "RECON_F2F_" + txnDate;
        LocalDateTime date = LocalDate.parse(txnDate, DATE).atStartOfDay();
        String windowStart = date.format(COMPACT);
        String windowEnd = date.plusDays(1).format(COMPACT);
        String facePayUrl = findFacePayUrl();

        ensureBatch(batchId, txnDate, windowStart, windowEnd);
        dispatch(batchId, txnDate, windowStart, windowEnd, facePayUrl);
        waitUntilDone(batchId);
        return readParts(batchId);
    }

    private void ensureBatch(String batchId, String txnDate, String windowStart, String windowEnd) {
        CreateBatchRequest request = new CreateBatchRequest();
        request.setBatchId(batchId);
        request.setBusinessDate(txnDate);
        request.setWindowStart(windowStart);
        request.setWindowEnd(windowEnd);
        batchService.create(request);
        sourceService.registerSingle(batchId, SOURCE, ReconFileType.valueOf(FILE_TYPE));
    }

    private void dispatch(String batchId, String txnDate, String windowStart, String windowEnd, String facePayUrl) {
        ReconExportReqDTO request = new ReconExportReqDTO();
        request.setBatchId(batchId);
        request.setBusinessDate(txnDate);
        request.setWindowStart(windowStart);
        request.setWindowEnd(windowEnd);
        request.setFileTypes(List.of(FILE_TYPE));
        request.validate();
        ReconExportRespDTO response = exportClient.dispatch(facePayUrl, request);
        if (!response.isAccepted()) {
            throw new IllegalStateException("face-pay F2F_TXN 抽取指令被回绝 batchId=" + batchId
                    + ", msg=" + response.getMessage());
        }
        log.info("已向 face-pay 下发 F2F_TXN 抽取指令 batchId={}, window=[{}, {})", batchId, windowStart, windowEnd);
    }

    private void waitUntilDone(String batchId) {
        long deadline = System.currentTimeMillis() + thirdPartyProperties.getFacePayFetchTimeoutMillis();
        long interval = Math.max(2000L, orchestration.getAdvanceDelayMillis());
        while (System.currentTimeMillis() < deadline) {
            SourceProgress progress = findProgress(batchId);
            if (progress != null) {
                if (progress.status() == ReconSourceStatus.COMPLETED) {
                    log.info("face-pay F2F_TXN 分片已收齐 batchId={}, records={}", batchId, progress.declaredRecords());
                    return;
                }
                if (progress.status() == ReconSourceStatus.FAILED || progress.status() == ReconSourceStatus.MISMATCH) {
                    throw new IllegalStateException("face-pay F2F_TXN 抽取失败 batchId=" + batchId
                            + ", status=" + progress.status() + ", reason=" + progress.failReason());
                }
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 face-pay F2F_TXN 分片被中断", ex);
            }
        }
        throw new IllegalStateException("等待 face-pay F2F_TXN 分片超时 batchId=" + batchId
                + ", timeout=" + thirdPartyProperties.getFacePayFetchTimeoutMillis() + "ms");
    }

    private SourceProgress findProgress(String batchId) {
        ReconFileType type = ReconFileType.valueOf(FILE_TYPE);
        for (SourceProgress p : sourceService.list(batchId)) {
            if (SOURCE.equals(p.source()) && type == p.fileType()) {
                return p;
            }
        }
        return null;
    }

    private List<ItpTxnRecord> readParts(String batchId) {
        List<ItpTxnRecord> records = new ArrayList<>();
        List<PartReceipt> parts = partMapper.selectReceivedParts(batchId, SOURCE, FILE_TYPE);
        if (parts.isEmpty()) {
            log.warn("face-pay F2F_TXN 无任何已接收分片，ITP 侧 face-pay 数据为空 batchId={}", batchId);
            return records;
        }
        Path root = Path.of(storageProperties.getStorageRoot()).toAbsolutePath().normalize();
        for (PartReceipt part : parts) {
            Path file = Path.of(part.path()).toAbsolutePath().normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                throw new IllegalStateException("F2F_TXN 分片路径非法或不存在: " + part.path());
            }
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    records.add(toRecord(F2fTxnLine.parse(line)));
                }
            } catch (IOException ex) {
                throw new IllegalStateException("读取 F2F_TXN 分片失败: " + part.path(), ex);
            }
        }
        log.info("已从分片读回 face-pay F2F 流水 batchId={}, records={}", batchId, records.size());
        return records;
    }

    private ItpTxnRecord toRecord(String[] f) {
        ItpTxnRecord r = new ItpTxnRecord();
        r.setTxnType(emptyToNull(f[F2fTxnLine.TXN_TYPE]));
        r.setLogicCardNo(emptyToNull(f[F2fTxnLine.LOGIC_CARD_NO]));
        r.setTxnDateTime(emptyToNull(f[F2fTxnLine.TXN_DATE_TIME]));
        r.setOriginalFare(toLongOrNull(f[F2fTxnLine.ORIGINAL_FARE]));
        r.setCurStationName(emptyToNull(f[F2fTxnLine.CUR_STATION_NAME]));
        r.setDeviceNo(emptyToNull(f[F2fTxnLine.DEVICE_NO]));
        r.setTicketType(emptyToNull(f[F2fTxnLine.TICKET_TYPE]));
        r.setTicketSubType(emptyToNull(f[F2fTxnLine.TICKET_SUB_TYPE]));
        r.setTimes(emptyToNull(f[F2fTxnLine.TIMES]));
        r.setPayChannel(emptyToNull(f[F2fTxnLine.PAY_CHANNEL]));
        r.setActivateTime(emptyToNull(f[F2fTxnLine.ACTIVATE_TIME]));
        r.setQuantity(emptyToNull(f[F2fTxnLine.QUANTITY]));
        r.setTicketTransSeq(emptyToNull(f[F2fTxnLine.TICKET_TRANS_SEQ]));
        r.setOrderNo(emptyToNull(f[F2fTxnLine.ORDER_NO]));
        r.setOrderAmount(toLongOrNull(f[F2fTxnLine.ORDER_AMOUNT]));
        r.setActualAmount(toLongOrNull(f[F2fTxnLine.ACTUAL_AMOUNT]));
        r.setDiscountCumulativeAmount(toLongOrNull(f[F2fTxnLine.DISCOUNT_CUMULATIVE_AMOUNT]));
        r.setOvertimeAmount(toLongOrNull(f[F2fTxnLine.OVERTIME_AMOUNT]));
        r.setMerchantOrderNo(emptyToNull(f[F2fTxnLine.MERCHANT_ORDER_NO]));
        r.setChannelTradeNo(emptyToNull(f[F2fTxnLine.CHANNEL_TRADE_NO]));
        r.setItpPayStatus(emptyToNull(f[F2fTxnLine.ITP_PAY_STATUS]));
        r.setSource(SOURCE);
        return r;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static Long toLongOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String findFacePayUrl() {
        for (ReconOrchestrationProperties.SourceExpectation e : orchestration.getSources()) {
            if (SOURCE.equals(e.getName())) {
                if (e.getUrl() == null || e.getUrl().isBlank()) {
                    throw new IllegalStateException("未配置 face-pay 对账源地址 recon.orchestration.sources[face-pay].url");
                }
                return e.getUrl();
            }
        }
        throw new IllegalStateException("对账期望清单里没有 face-pay 源，无法拉取 F2F_TXN");
    }
}
