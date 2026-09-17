package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.mapper.F2fReconExportMapper;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconFileTypeEnum;
import com.chinasofti.huateng.model.recon.ReconRecord;
import com.chinasofti.huateng.rpc.recon.ReconPartSink;
import com.chinasofti.huateng.rpc.recon.ReconPartUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 日终对账分片导出，本源标识 {@code face-pay}，只负责 ITP.PAY 与 ITP.BUS 两类文件。本类刻意不带 {@code @Transactional}（抽取只读、链路里夹着 RPC），NEVER 加。 */
@Service
public class ReconExportService {

    private static final Logger log = LoggerFactory.getLogger(ReconExportService.class);

    /** 本源标识，跨服务契约的第一段字段值，MUST 与 recon-server 的 {@code sources[n].name} 一致，NEVER 改。 */
    private static final String SOURCE = "face-pay";

    /** ITP.PAY 每行段数：前 5 段键 + 后 16 段度量，来自甲方规格，NEVER 调整。 */
    private static final int PAY_FIELD_COUNT = 21;

    /** ITP.PAY 度量段的起始下标（0 基），前 5 段是键。 */
    private static final int PAY_METRIC_BEGIN = 5;

    /** BOM/TVM 发售笔数的下标，金额紧随其后（idx 6）。 */
    private static final int PAY_IDX_SALE_COUNT = 5;

    /** BOM/TVM 充值笔数的下标，金额紧随其后（idx 8）。 */
    private static final int PAY_IDX_TOPUP_COUNT = 7;

    /** APP 购票笔数的下标，金额紧随其后（idx 14）。 */
    private static final int PAY_IDX_APP_COUNT = 13;

    /** BOM 行政处理笔数的下标，金额紧随其后（idx 16）。 */
    private static final int PAY_IDX_BOM_ADMIN_COUNT = 15;

    /** BOM 处理笔数的下标，金额紧随其后（idx 20）。 */
    private static final int PAY_IDX_BOM_OTHER_COUNT = 19;

    private final F2fReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final ExecutorService executor;

    /** 在途批次标记。 */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * @param workerCount 抽取线程数，取自配置 {@code recon.export.worker}；默认 1 即串行，
     */
    public ReconExportService(F2fReconExportMapper reconExportMapper,
                              ReconPartUploader uploader,
                              @Value("${recon.export.worker:1}") int workerCount) {
        this.reconExportMapper = reconExportMapper;
        this.uploader = uploader;
        this.executor = Executors.newFixedThreadPool(Math.max(1, workerCount), r -> {
            Thread t = new Thread(r, "recon-export");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 受理一次抽取指令：校验、去重、派给抽取线程池，立即返回。
     *
     * @param request recon-server 下发的抽取指令
     * @return true 已受理；false 同批次上一轮抽取仍在执行（限流，不是失败）
     */
    public boolean submit(ReconExportReqDTO request) {
        request.validate();
        String batchId = request.getBatchId();
        if (!running.add(batchId)) {
            log.warn("同批次对账抽取仍在执行，本次指令丢弃 batchId={}, source={}", batchId, SOURCE);
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    runExport(request);
                } finally {
                    running.remove(batchId);
                }
            });
        } catch (RuntimeException ex) {
            running.remove(batchId);
            throw ex;
        }
        log.info("已受理对账抽取指令 batchId={}, source={}, businessDate={}, window=[{}, {}), fileTypes={}",
                batchId, SOURCE, request.getBusinessDate(),
                request.getWindowStart(), request.getWindowEnd(), request.getFileTypes());
        return true;
    }

    /** 在抽取线程上按 fileTypes 逐类导出。 */
    private void runExport(ReconExportReqDTO request) {
        warnOnLeakage(request);
        List<String> failures = new ArrayList<>();
        for (String fileType : request.getFileTypes()) {
            ReconFileTypeEnum type = ReconFileTypeEnum.of(fileType);
            try {
                switch (type) {
                    case PAY -> exportPay(request);
                    case BUS -> exportBus(request);
                    default -> log.warn("face-pay 不负责该对账文件类型，已跳过 batchId={}, fileType={}",
                            request.getBatchId(), type);
                }
            } catch (RuntimeException ex) {
                failures.add(type.name());
                log.error("对账抽取失败 batchId={}, source={}, fileType={}, msg={}",
                        request.getBatchId(), SOURCE, type, ex.getMessage(), ex);
            }
        }
        if (!failures.isEmpty()) {
            log.error("本轮对账抽取存在失败文件类型 batchId={}, source={}, failed={}",
                    request.getBatchId(), SOURCE, failures);
        }
    }

    /** 两个漏账探针，只记日志、不影响抽取结果。 */
    private void warnOnLeakage(ReconExportReqDTO request) {
        Timestamp windowStart = request.getWindowStartTimestamp();
        Timestamp windowEnd = request.getWindowEndTimestamp();
        try {
            long uncovered = nullToZero(reconExportMapper.countUncoveredPaidOrders(windowStart, windowEnd));
            if (uncovered > 0) {
                log.warn("窗口内存在业务类型未纳入对账口径的成功支付单，这些钱不会进任何度量段 "
                        + "batchId={}, source={}, count={}", request.getBatchId(), SOURCE, uncovered);
            }
            long missing = nullToZero(reconExportMapper.countPaidWithoutPaidTms(windowStart, windowEnd));
            if (missing > 0) {
                log.warn("窗口内存在支付成功但订单 PAID_TMS 为空的残缺行，本轮汇总会漏掉它们 "
                        + "batchId={}, source={}, count={}", request.getBatchId(), SOURCE, missing);
            }
        } catch (RuntimeException ex) {
            log.error("漏账探针执行失败，不影响本轮抽取 batchId={}, source={}, msg={}",
                    request.getBatchId(), SOURCE, ex.getMessage(), ex);
        }
    }

    /** ITP.PAY 统计汇总导出：五条库内 GROUP BY 的结果依次写入同一个 sink。 */
    private void exportPay(ReconExportReqDTO request) {
        Timestamp windowStart = request.getWindowStartTimestamp();
        Timestamp windowEnd = request.getWindowEndTimestamp();
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.PAY)) {
            for (Map<String, Object> row : reconExportMapper.selectDeviceSalePaySummary(windowStart, windowEnd)) {
                writePayRow(sink, row, PAY_IDX_SALE_COUNT);
            }
            for (Map<String, Object> row : reconExportMapper.selectTopupPaySummary(windowStart, windowEnd)) {
                writePayRow(sink, row, PAY_IDX_TOPUP_COUNT);
            }
            for (Map<String, Object> row : reconExportMapper.selectAppSalePaySummary(windowStart, windowEnd)) {
                writePayRow(sink, row, PAY_IDX_APP_COUNT);
            }
            for (Map<String, Object> row : reconExportMapper.selectBomAdminPaySummary(windowStart, windowEnd)) {
                writePayRow(sink, row, PAY_IDX_BOM_ADMIN_COUNT);
            }
            for (Map<String, Object> row : reconExportMapper.selectBomOtherPaySummary(windowStart, windowEnd)) {
                writePayRow(sink, row, PAY_IDX_BOM_OTHER_COUNT);
            }
            sink.commit();
            log.info("PAY 抽取完成 batchId={}, parts={}, records={}, amount={}",
                    request.getBatchId(), sink.getUploadedParts(), sink.getTotalRecords(), sink.getTotalAmount());
        }
    }

    /**
     * 写一行 ITP.PAY：21 段，只有 {@code countIndex} 与 {@code countIndex + 1} 两段度量非 0。
     *
     * @param row        聚合结果行
     * @param countIndex 本组「笔数」在 21 段里的下标，金额固定为它 + 1
     */
    private void writePayRow(ReconPartSink sink, Map<String, Object> row, int countIndex) {
        long count = toLong(row.get("TXN_COUNT"));
        long amount = toLong(row.get("TXN_AMOUNT"));
        Object[] fields = new Object[PAY_FIELD_COUNT];
        Arrays.fill(fields, PAY_METRIC_BEGIN, PAY_FIELD_COUNT, 0L);
        fields[0] = toStr(row.get("TXN_DATE"));
        fields[1] = "";
        fields[2] = toStr(row.get("STATION_CODE"));
        fields[3] = toStr(row.get("DEVICE_ID"));
        fields[4] = toStr(row.get("PAY_CHANNEL_CODE"));
        fields[countIndex] = count;
        fields[countIndex + 1] = amount;
        sink.write(ReconRecord.line(fields), amount);
    }

    /** ITP.BUS 商业优惠汇总导出：只有 4 段 = 1 段键（日期）+ 3 段度量， 行格式 {@code 日期|对账金额|付款金额|优惠金额}（字段顺序来自甲方规格，NEVER 调整）。 */
    private void exportBus(ReconExportReqDTO request) {
        Timestamp windowStart = request.getWindowStartTimestamp();
        Timestamp windowEnd = request.getWindowEndTimestamp();
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.BUS)) {
            for (Map<String, Object> row : reconExportMapper.selectBusSummary(windowStart, windowEnd)) {
                long amount = toLong(row.get("TXN_AMOUNT"));
                sink.write(ReconRecord.line(toStr(row.get("TXN_DATE")), amount, amount, 0L), amount);
            }
            sink.commit();
            log.info("BUS 抽取完成 batchId={}, parts={}, records={}, amount={}",
                    request.getBatchId(), sink.getUploadedParts(), sink.getTotalRecords(), sink.getTotalAmount());
        }
    }

    /** Oracle 数字列转 long（单位分）。 */
    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0L;
        }
        return new BigDecimal(text).longValue();
    }

    /** Map 取字符串，null 保持 null 交由 {@link ReconRecord#line(Object...)} 统一转空串。 */
    private String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 探针 {@code COUNT(*)} 理论上不会返 null，仍兜一层，NEVER 直接拆箱。 */
    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
