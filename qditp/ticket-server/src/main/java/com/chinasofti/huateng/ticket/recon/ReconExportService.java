package com.chinasofti.huateng.ticket.recon;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 日终对账分片导出，本源标识 {@code ticket}，负责 EXP（进出站交易明细）与 BUS（业务汇总）两类文件。 */
@Service
public class ReconExportService {

    private static final Logger log = LoggerFactory.getLogger(ReconExportService.class);

    /** 本源标识，EXP 行首 sourceType 段的取值，跨服务契约， */
    private static final String SOURCE = "ticket";

    private final ReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final int pageSize;

    private final ExecutorService executor;

    /** 在途批次标记，元素为 batchId。 */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * @param pageSize EXP 每批查询行数，取自配置 {@code recon.export.page-size}
     * @param workerCount 抽取线程数，取自配置 {@code recon.export.worker}；
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
     * @param request recon-server 下发的抽取指令
     * @return true 已受理；
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
    void runExport(ReconExportReqDTO request) {
        List<String> failures = new ArrayList<>();
        for (String fileType : request.getFileTypes()) {
            ReconFileTypeEnum type = ReconFileTypeEnum.of(fileType);
            try {
                switch (type) {
                    case EXP -> exportExp(request);
                    case BUS -> exportBus(request);
                    default -> log.warn("ticket 不负责该对账文件类型，已跳过 batchId={}, fileType={}",
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
    /** EXP 明细导出：keyset 游标逐批查询、逐行写入分片通道。 */
    private void exportExp(ReconExportReqDTO request) {
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.EXP)) {
            String lastHandleTime = null;
            Long lastId = null;
            while (true) {
                List<Map<String, Object>> rows = reconExportMapper.selectExpPage(
                        request.getStartDate(), request.getEndDate(),
                        request.getWindowStart(), request.getWindowEnd(),
                        lastHandleTime, lastId, pageSize);
                if (rows.isEmpty()) {
                    break;
                }
                for (Map<String, Object> row : rows) {
                    long trxAmount = toLong(row.get("TRX_AMOUNT_TOTAL"));
                    String line = ReconRecord.line(
                            SOURCE,
                            toStr(row.get("TXN_DATE")),
                            toStr(row.get("HANDLE_DATE_TIME")),
                            toStr(row.get("LINE_CODE")),
                            toStr(row.get("HANDLE_STATION_CODE")),
                            toStr(row.get("DEVICE_ID")),
                            toStr(row.get("CARD_TYPE")),
                            toStr(row.get("CARD_ID")),
                            toStr(row.get("TRX_TYPE")),
                            trxAmount,
                            toStr(row.get("RESERVE1")),
                            toStr(row.get("ITP_USER_ID")));
                    sink.write(line, trxAmount);
                }
                Map<String, Object> last = rows.get(rows.size() - 1);
                lastHandleTime = toStr(last.get("HANDLE_DATE_TIME"));
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
    /** BUS 汇总导出：数据库内 GROUP BY 后一次查回，逐行写入。 */
    private void exportBus(ReconExportReqDTO request) {
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.BUS)) {
            List<Map<String, Object>> rows = reconExportMapper.selectBusSummary(
                    request.getStartDate(), request.getEndDate(),
                    request.getWindowStart(), request.getWindowEnd());
            for (Map<String, Object> row : rows) {
                long txnAmount = toLong(row.get("TXN_AMOUNT"));
                String line = ReconRecord.line(
                        toStr(row.get("TXN_DATE")),
                        toStr(row.get("LINE_CODE")),
                        toStr(row.get("HANDLE_STATION_CODE")),
                        toStr(row.get("DEVICE_ID")),
                        toStr(row.get("CARD_TYPE")),
                        toLong(row.get("TXN_COUNT")),
                        txnAmount);
                sink.write(line, txnAmount);
            }
            sink.commit();
            log.info("BUS 抽取完成 batchId={}, parts={}, records={}, amount={}",
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
