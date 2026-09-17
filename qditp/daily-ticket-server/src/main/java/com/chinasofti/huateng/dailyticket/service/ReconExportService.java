package com.chinasofti.huateng.dailyticket.service;

import com.chinasofti.huateng.dailyticket.mapper.ReconExportMapper;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconFileTypeEnum;
import com.chinasofti.huateng.model.recon.ReconRecord;
import com.chinasofti.huateng.rpc.recon.ReconPartSink;
import com.chinasofti.huateng.rpc.recon.ReconPartUploader;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 日终对账分片导出（来源标识 {@code daily-ticket}，负责 DETAIL 与 PAY 两类文件）。 */
@Service
public class ReconExportService {

    /** 本源在对账体系里的来源标识，与 recon-server 注册的源名一致，NEVER 改。 */
    public static final String SOURCE = "daily-ticket";

    /** DETAIL 第 2 段「交易类型」在车票购买场景下的固定取值，甲方原文即中文「发售」。 */
    private static final String TXN_TYPE_SALE = "发售";

    private static final Logger log = LoggerFactory.getLogger(ReconExportService.class);

    private final ReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final int pageSize;

    private final ExecutorService executor;

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
     *
     *
     * @param reconExportMapper 抽取用只读 Mapper
     * @param uploader          分片上送通道工厂（rpc 模块提供）
     * @param pageSize          Keyset 单页行数，取自 {@code recon.export.page-size}
     * @param worker            抽取线程数，取自 {@code recon.export.worker}，默认 1 即串行
     */
    public ReconExportService(ReconExportMapper reconExportMapper,
                              ReconPartUploader uploader,
                              @Value("${recon.export.page-size:5000}") int pageSize,
                              @Value("${recon.export.worker:1}") int worker) {
        this.reconExportMapper = reconExportMapper;
        this.uploader = uploader;
        this.pageSize = pageSize > 0 ? pageSize : 5000;
        this.executor = Executors.newFixedThreadPool(worker > 0 ? worker : 1, r -> {
            Thread t = new Thread(r, "recon-export");
            t.setDaemon(true);
            return t;
        });
    }

    /** 关闭抽取线程池，避免容器停机时留下悬挂线程。 */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 受理抽取指令：校验后排入抽取线程池，**立即返回**。
     *
     * @param request recon-server 下发的抽取指令
     * @return true 已受理；false 同批次仍在途，本次丢弃（限流语义）
     */
    public boolean submit(ReconExportReqDTO request) {
        request.validate();
        String batchId = request.getBatchId();
        if (!inFlight.add(batchId)) {
            log.warn("对账抽取指令被回绝，同批次仍在途 batchId={}, source={}", batchId, SOURCE);
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    runExport(request);
                } finally {
                    inFlight.remove(batchId);
                }
            });
        } catch (RuntimeException ex) {
            inFlight.remove(batchId);
            throw ex;
        }
        return true;
    }

    /**
     * 执行抽取：按 {@code fileTypes} 分派，每类文件独立 try-with-resources + {@code commit()}。
     *
     * @param req 抽取指令
     */
    public void runExport(ReconExportReqDTO req) {
        for (String fileType : req.getFileTypes()) {
            ReconFileTypeEnum type = ReconFileTypeEnum.of(fileType);
            try {
                switch (type) {
                    case DETAIL -> exportDetail(req);
                    case PAY -> exportPaySummary(req);
                    default -> log.warn("daily-ticket 不负责该对账文件类型，已跳过 batchId={}, fileType={}",
                            req.getBatchId(), type);
                }
            } catch (RuntimeException ex) {
                log.error("对账抽取失败 batchId={}, source={}, fileType={}, msg={}",
                        req.getBatchId(), SOURCE, type, ex.getMessage(), ex);
            }
        }
    }

    /** DETAIL：虚拟电子多日计次票的「车票购买（发售）」明细，Keyset 游标翻页逐行写出。 */
    private void exportDetail(ReconExportReqDTO req) {
        Timestamp windowStart = req.getWindowStartTimestamp();
        Timestamp windowEnd = req.getWindowEndTimestamp();
        try (ReconPartSink sink = uploader.open(req.getBatchId(), SOURCE, ReconFileTypeEnum.DETAIL)) {
            Timestamp lastPayDate = null;
            String lastOrderNo = null;
            while (true) {
                List<Map<String, Object>> rows =
                        reconExportMapper.selectDetailPage(windowStart, windowEnd, lastPayDate, lastOrderNo, pageSize);
                if (rows == null || rows.isEmpty()) {
                    break;
                }
                for (Map<String, Object> row : rows) {
                    long payAmount = toLong(pick(row, "PAY_AMOUNT"));
                    sink.write(ReconRecord.line(
                            toStr(pick(row, "OPERATE_DATE")),
                            TXN_TYPE_SALE,
                            toStr(pick(row, "CARD_NUM")),
                            toStr(pick(row, "TXN_DATE_TIME")),
                            payAmount,
                            "",
                            ""), payAmount);
                }
                Map<String, Object> last = rows.get(rows.size() - 1);
                lastPayDate = toTimestamp(pick(last, "PAY_DATE"));
                lastOrderNo = toStr(pick(last, "ORDER_NO"));
                if (lastPayDate == null || lastOrderNo.isEmpty()) {
                    throw new IllegalStateException("对账明细游标缺失，拒绝继续翻页以免漏行或死循环 batchId="
                            + req.getBatchId());
                }
                if (rows.size() < pageSize) {
                    break;
                }
            }
            sink.commit();
        }
    }

    /** PAY：统计汇总，库内 GROUP BY 后一次查完逐行写出。 */
    private void exportPaySummary(ReconExportReqDTO req) {
        List<Map<String, Object>> rows = reconExportMapper.selectTravelTicketPaySummary(
                req.getWindowStartTimestamp(), req.getWindowEndTimestamp());
        try (ReconPartSink sink = uploader.open(req.getBatchId(), SOURCE, ReconFileTypeEnum.PAY)) {
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    long ticketCount = toLong(pick(row, "TICKET_COUNT"));
                    long ticketAmount = toLong(pick(row, "TICKET_AMOUNT"));
                    sink.write(ReconRecord.line(
                            toStr(pick(row, "TXN_DATE")),
                            "",
                            "",
                            "",
                            toStr(pick(row, "PAY_CHANNEL_CODE")),
                            0L, 0L,
                            0L, 0L,
                            ticketCount, ticketAmount,
                            0L, 0L,
                            0L, 0L,
                            0L, 0L,
                            0L, 0L,
                            0L, 0L), ticketAmount);
                }
            }
            sink.commit();
        }
    }

    /** 从结果 Map 里取列值。 */
    private Object pick(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value != null) {
            return value;
        }
        return row.get(camel(column));
    }

    /** 把 {@code PAY_CHANNEL_CODE} 这类列名转成 {@code payChannelCode}。 */
    private String camel(String column) {
        StringBuilder builder = new StringBuilder(column.length());
        boolean upperNext = false;
        for (int i = 0; i < column.length(); i++) {
            char c = column.charAt(i);
            if (c == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
            upperNext = false;
        }
        return builder.toString();
    }

    /** null 安全的字符串化，null 转空串（分片行里空字段就是空串，不是字面量 null）。 */
    private String toStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 金额与笔数一律按 long 取。Oracle 的 {@code NUMBER} 经 ojdbc8 回来通常是 {@link BigDecimal}，
     * 少数聚合列可能是 {@link Long} / {@link Integer}，都在这里收口。
     */
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

    /** 把结果 Map 里的时间列转成 {@link Timestamp}，供 Keyset 游标回填。 */
    private Timestamp toTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (value instanceof Date date) {
            return new Timestamp(date.getTime());
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return Timestamp.valueOf(localDateTime);
        }
        try {
            Object converted = value.getClass().getMethod("timestampValue").invoke(value);
            return converted instanceof Timestamp timestamp ? timestamp : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.error("无法识别的时间列类型 type={}, msg={}", value.getClass().getName(), ex.getMessage());
            return null;
        }
    }
}
