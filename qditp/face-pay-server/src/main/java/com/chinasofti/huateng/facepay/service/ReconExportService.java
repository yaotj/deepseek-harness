package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.mapper.F2fReconExportMapper;
import com.chinasofti.huateng.model.recon.F2fTxnLine;
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

@Service
public class ReconExportService {

    private static final Logger log = LoggerFactory.getLogger(ReconExportService.class);

    private static final String SOURCE = "face-pay";

    private static final int PAY_FIELD_COUNT = 21;

    private static final int PAY_METRIC_BEGIN = 5;

    private static final int PAY_IDX_SALE_COUNT = 5;

    private static final int PAY_IDX_TOPUP_COUNT = 7;

    private static final int PAY_IDX_APP_COUNT = 13;

    private static final int PAY_IDX_BOM_ADMIN_COUNT = 15;

    private static final int PAY_IDX_BOM_OTHER_COUNT = 19;

    private final F2fReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final ExecutorService executor;

    private final Set<String> running = ConcurrentHashMap.newKeySet();

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

    private void runExport(ReconExportReqDTO request) {
        warnOnLeakage(request);
        List<String> failures = new ArrayList<>();
        for (String fileType : request.getFileTypes()) {
            ReconFileTypeEnum type = ReconFileTypeEnum.of(fileType);
            try {
                switch (type) {
                    case PAY -> exportPay(request);
                    case BUS -> exportBus(request);
                    case F2F_TXN -> exportF2fTxn(request);
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

    private void exportF2fTxn(ReconExportReqDTO request) {
        Timestamp windowStart = request.getWindowStartTimestamp();
        Timestamp windowEnd = request.getWindowEndTimestamp();
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.F2F_TXN)) {
            for (Map<String, Object> row : reconExportMapper.selectFacePayTxnDetails(windowStart, windowEnd)) {
                sink.write(buildF2fTxnLine(row), f2fTxnAmountCents(row));
            }
            sink.commit();
            log.info("F2F_TXN 抽取完成 batchId={}, parts={}, records={}, amount={}",
                    request.getBatchId(), sink.getUploadedParts(), sink.getTotalRecords(), sink.getTotalAmount());
        }
    }

    private String buildF2fTxnLine(Map<String, Object> row) {
        String[] f = new String[F2fTxnLine.FIELD_COUNT];
        f[F2fTxnLine.TXN_TYPE] = "当面付";
        f[F2fTxnLine.LOGIC_CARD_NO] = toStr(row.get("CARD_ID"));
        f[F2fTxnLine.TXN_DATE_TIME] = toStr(row.get("PAID_TMS_STR"));
        f[F2fTxnLine.ORIGINAL_FARE] = String.valueOf(toLong(row.get("TICKET_PRICE")));
        f[F2fTxnLine.CUR_STATION_NAME] = toStr(row.get("STATION_CODE"));
        f[F2fTxnLine.DEVICE_NO] = toStr(row.get("DEVICE_ID"));
        f[F2fTxnLine.TICKET_TYPE] = "";
        f[F2fTxnLine.TICKET_SUB_TYPE] = "";
        f[F2fTxnLine.TIMES] = toStr(row.get("TICKET_NUM"));
        f[F2fTxnLine.PAY_CHANNEL] = toStr(row.get("PAY_CHANNEL_CODE"));
        f[F2fTxnLine.ACTIVATE_TIME] = toStr(row.get("ACTIVATE_TMS_STR"));
        f[F2fTxnLine.QUANTITY] = "1";
        f[F2fTxnLine.TICKET_TRANS_SEQ] = toStr(row.get("DEVICE_SEQ"));
        f[F2fTxnLine.ORDER_NO] = toStr(row.get("ORDER_NO"));
        f[F2fTxnLine.ORDER_AMOUNT] = String.valueOf(toLong(row.get("ORDER_AMOUNT")));
        f[F2fTxnLine.ACTUAL_AMOUNT] = String.valueOf(toLong(row.get("ACTUAL_AMOUNT")));
        f[F2fTxnLine.DISCOUNT_CUMULATIVE_AMOUNT] = String.valueOf(toLong(row.get("COUPON_AMOUNT")));
        f[F2fTxnLine.OVERTIME_AMOUNT] = "";
        f[F2fTxnLine.MERCHANT_ORDER_NO] = toStr(row.get("ORDER_NO"));
        f[F2fTxnLine.CHANNEL_TRADE_NO] = toStr(row.get("CHANNEL_ORDER_NO"));
        f[F2fTxnLine.ITP_PAY_STATUS] = toStr(row.get("PAY_STATUS"));
        return F2fTxnLine.toLine(f);
    }

    private long f2fTxnAmountCents(Map<String, Object> row) {
        return toLong(row.get("ACTUAL_AMOUNT"));
    }

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

    private String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
