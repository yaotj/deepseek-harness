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

/** 日终对账分片导出，本源标识 {@code gate-txn-pay} */
@Service
public class ReconExportService {

    private static final Logger log = LoggerFactory.getLogger(ReconExportService.class);

    /** 本源标识，跨服务契约字段值。 */
    private static final String SOURCE = "gate-txn-pay";

    /** PAY 行总段数：5 段键 + 16 段度量，甲方 §一(2) 明确列出。 */
    private static final int PAY_FIELD_COUNT = 21;

    /** PAY 行「过闸笔数」下标（甲方第 12 段，0 基下标 11），紧邻的下一段是「过闸金额」。 */
    private static final int PAY_IDX_GATE_COUNT = 11;

    /** PAY 行「单边交易笔数」下标（甲方第 18 段，0 基下标 17），紧邻的下一段是「单边交易金额」。 */
    private static final int PAY_IDX_EXP_COUNT = 17;

    /** DETAIL 行「交易类型」的固定取值。 */
    private static final String DETAIL_TXN_TYPE_EXIT = "出站";

    private final ReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final int pageSize;

    private final ExecutorService executor;

    /** 在途批次标记，key 为 batchId。 */
    private final ConcurrentHashMap<String, Boolean> running = new ConcurrentHashMap<>();

    /**
     * @param pageSize 明细类（EXP / DETAIL）每批查询行数，取自配置 {@code recon.export.page-size}
     * @param workerCount 抽取线程数，取自配置 {@code recon.export.worker}，默认 1 即串行。
     */
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

    /**
     * 受理一次抽取指令：校验、去重、派给抽取线程池，立即返回。
     *
     * @param request recon-server 下发的抽取指令。
     * @return true 已受理，false 同批次上一轮抽取仍在执行（限流，不是失败）。
     */
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

    /** 在抽取线程上按 fileTypes 逐类导出。 */
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

    /** EXP 单边交易明细导出：keyset 游标逐批查询、逐行写入分片通道。 */
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

    /** PAY 统计汇总导出：两条库内 GROUP BY，各自输出一行。 */
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

    /**
     * 拼一条 PAY 行：5 段键照抄，16 段度量默认字面 {@code 0}，只把指定的一组笔数/金额填进去。
     *
     * @param row 聚合结果行，含 TXN_DATE / OUT_STATION / DEVICE_ID / SIGN_CHANNEL_CODE。
     * @param countIndex 笔数所在的 0 基下标。
     * @param count 笔数。
     * @param amount 金额，单位分。
     */
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

    /** BUS 商业优惠汇总导出：一条库内 GROUP BY，逐行写入。 */
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

    /** DETAIL 虚拟电子多日计次票明细导出：keyset 游标逐批查询、逐行写入。 */
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
                            overtimeAmount,
                            toStr(row.get("EXIT_STATION_NAME")),
                            toStr(row.get("DEVICE_ID")));
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

    /** Oracle 数字列转 long。 */
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
}
