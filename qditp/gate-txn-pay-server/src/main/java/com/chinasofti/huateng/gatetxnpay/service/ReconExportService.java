package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.gatetxnpay.mapper.ReconExportMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ReconExportService {

    private static final Logger log = LoggerFactory.getLogger(ReconExportService.class);

    private static final String SOURCE = "gate-txn-pay";

    private static final int PAY_FIELD_COUNT = 21;

    private static final int PAY_IDX_GATE_COUNT = 11;

    private static final int PAY_IDX_EXP_COUNT = 17;

    private static final String DETAIL_TXN_TYPE_EXIT = "出站";

    private final ReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final int pageSize;

    private final ExecutorService executor;

    private final ConcurrentHashMap<String, Boolean> running = new ConcurrentHashMap<>();


    public ReconExportService(ReconExportMapper reconExportMapper,
                              ReconPartUploader uploader,
                              @Value("${recon.export.page-size:5000}") int pageSize,
                              @Value("${recon.export.worker:1}") int workerCount) {
        this.reconExportMapper = reconExportMapper;
        this.uploader = uploader;
        this.pageSize = pageSize;
        this.executor = Executors.newFixedThreadPool(Math.max(1, workerCount), r -> {
            Thread t = new Thread(r, "recon-export");
            t.setDaemon(true);
            return t;
        });
    }


    public boolean submit(ReconExportReqDTO request) {
        request.validate();
        String batchId = request.getBatchId();
        if (running.putIfAbsent(batchId, Boolean.TRUE) != null) {
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
        List<String> failures = new ArrayList<>();
        for (String fileType : request.getFileTypes()) {
            ReconFileTypeEnum type = ReconFileTypeEnum.of(fileType);
            try {
                switch (type) {
                    case EXP -> exportExp(request);
                    case PAY -> exportPay(request);
                    case BUS -> exportBus(request);
                    case DETAIL -> exportDetail(request);
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

    private void exportExp(ReconExportReqDTO request) {
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.EXP)) {
            String lastOutTime = null;
            Long lastId = null;
            while (true) {
                List<Map<String, Object>> rows = reconExportMapper.selectExpPage(
                        request.getStartDate(), request.getEndDate(),
                        request.getWindowStart(), request.getWindowEnd(),
                        lastOutTime, lastId, pageSize);
                if (rows.isEmpty()) {
                    break;
                }
                for (Map<String, Object> row : rows) {
                    long totalAmount = toLong(row.get("TOTAL_AMOUNT"));
                    String line = ReconRecord.line(
                            toStr(row.get("CARD_ID")),
                            toStr(row.get("TICKET_TRANS_SEQ")),
                            totalAmount,
                            totalAmount,
                            0L,
                            "",
                            toStr(row.get("IN_TIME")),
                            "",
                            toStr(row.get("DEVICE_ID")),
                            toStr(row.get("OUT_TIME")),
                            toStr(row.get("ORDER_EXP_TYPE")),
                            toStr(row.get("SIGN_CHANNEL_CODE")),
                            toStr(row.get("TXN_DATE")));
                    sink.write(line, totalAmount);
                }
                Map<String, Object> last = rows.get(rows.size() - 1);
                lastOutTime = toStr(last.get("OUT_TIME"));
                lastId = toLong(last.get("ID"));
                if (rows.size() < pageSize) {
                    break;
                }
            }
            sink.commit();
            log.info("EXP 抽取完成 batchId={}, parts={}, records={}, amount={}",
                    request.getBatchId(), sink.getUploadedParts(), sink.getTotalRecords(), sink.getTotalAmount());
        }
    }

    private void exportPay(ReconExportReqDTO request) {
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.PAY)) {
            List<Map<String, Object>> gateRows = reconExportMapper.selectPayGateSummary(
                    request.getStartDate(), request.getEndDate(),
                    request.getWindowStart(), request.getWindowEnd());
            for (Map<String, Object> row : gateRows) {
                long amount = toLong(row.get("PAY_AMOUNT"));
                sink.write(payLine(row, PAY_IDX_GATE_COUNT, toLong(row.get("TXN_COUNT")), amount), amount);
            }
            List<Map<String, Object>> expRows = reconExportMapper.selectPayExpSummary(
                    request.getStartDate(), request.getEndDate(),
                    request.getWindowStart(), request.getWindowEnd());
            for (Map<String, Object> row : expRows) {
                long amount = toLong(row.get("PAY_AMOUNT"));
                sink.write(payLine(row, PAY_IDX_EXP_COUNT, toLong(row.get("TXN_COUNT")), amount), amount);
            }
            sink.commit();
            log.info("PAY 抽取完成 batchId={}, gateGroups={}, expGroups={}, parts={}, records={}, amount={}",
                    request.getBatchId(), gateRows.size(), expRows.size(),
                    sink.getUploadedParts(), sink.getTotalRecords(), sink.getTotalAmount());
        }
    }


    private String payLine(Map<String, Object> row, int countIndex, long count, long amount) {
        Object[] fields = new Object[PAY_FIELD_COUNT];
        fields[0] = toStr(row.get("TXN_DATE"));
        fields[1] = "";
        fields[2] = toStr(row.get("OUT_STATION"));
        fields[3] = toStr(row.get("DEVICE_ID"));
        fields[4] = toStr(row.get("SIGN_CHANNEL_CODE"));
        for (int i = 5; i < PAY_FIELD_COUNT; i++) {
            fields[i] = "0";
        }
        fields[countIndex] = count;
        fields[countIndex + 1] = amount;
        return ReconRecord.line(fields);
    }

    private void exportBus(ReconExportReqDTO request) {
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.BUS)) {
            List<Map<String, Object>> rows = reconExportMapper.selectBusSummary(
                    request.getStartDate(), request.getEndDate(),
                    request.getWindowStart(), request.getWindowEnd());
            for (Map<String, Object> row : rows) {
                long reconAmount = toLong(row.get("RECON_AMOUNT"));
                String line = ReconRecord.line(
                        toStr(row.get("TXN_DATE")),
                        reconAmount,
                        toLong(row.get("PAY_AMOUNT")),
                        0L);
                sink.write(line, reconAmount);
            }
            sink.commit();
            log.info("BUS 抽取完成 batchId={}, groups={}, parts={}, records={}, amount={}",
                    request.getBatchId(), rows.size(),
                    sink.getUploadedParts(), sink.getTotalRecords(), sink.getTotalAmount());
        }
    }

    private void exportDetail(ReconExportReqDTO request) {
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.DETAIL)) {
            String lastOutTime = null;
            Long lastId = null;
            while (true) {
                List<Map<String, Object>> rows = reconExportMapper.selectDetailPage(
                        request.getStartDate(), request.getEndDate(),
                        request.getWindowStart(), request.getWindowEnd(),
                        lastOutTime, lastId, pageSize);
                if (rows.isEmpty()) {
                    break;
                }
                for (Map<String, Object> row : rows) {
                    long overtimeAmount = toLong(row.get("OVERTIME_AMOUNT"));
                    String line = ReconRecord.line(
                            toStr(row.get("TXN_DATE")),
                            DETAIL_TXN_TYPE_EXIT,
                            toStr(row.get("CARD_ID")),
                            toStr(row.get("OUT_TIME")),
                            toLong(row.get("ORIGINAL_FARE")),
                            toStr(row.get("EXIT_STATION_NAME")),
                            toStr(row.get("DEVICE_ID")),
                            toStr(row.get("CARD_TYPE")),
                            "",
                            toLong(row.get("COUNTING_TIMES")),
                            toStr(row.get("SIGN_CHANNEL_CODE")),
                            "",
                            "1",
                            toStr(row.get("TICKET_TRANS_SEQ")),
                            toStr(row.get("ORDER_NO")),
                            toLong(row.get("TOTAL_AMOUNT")),
                            toLong(row.get("TOTAL_AMOUNT")),
                            "",
                            overtimeAmount,
                            "超时订单",
                            "");
                    sink.write(line, overtimeAmount);
                }
                Map<String, Object> last = rows.get(rows.size() - 1);
                lastOutTime = toStr(last.get("OUT_TIME"));
                lastId = toLong(last.get("ID"));
                if (rows.size() < pageSize) {
                    break;
                }
            }
            sink.commit();
            log.info("DETAIL 抽取完成 batchId={}, parts={}, records={}, amount={}",
                    request.getBatchId(), sink.getUploadedParts(), sink.getTotalRecords(), sink.getTotalAmount());
        }
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
}
