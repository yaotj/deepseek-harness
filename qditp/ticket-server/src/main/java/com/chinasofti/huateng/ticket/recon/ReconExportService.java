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

/**
 * 日终对账分片导出，本源标识 {@code ticket}，负责 EXP（进出站交易明细）与 BUS（业务汇总）两类文件。
 *
 * <p><b>抽取绝不能跑在请求线程上。</b>全服务默认 {@code spring.threads.virtual.enabled=true}
 * （{@code resource/micro/web/src/main/resources/web.properties:6}），Tomcat 处理线程是虚拟线程。
 * JDK 21 未落地 JEP 491，虚拟线程在 {@code synchronized} 内阻塞会 <b>pin 住载体线程</b>，
 * 而 ojdbc8 的 {@code PhysicalConnection} / {@code OracleStatement} 大量方法是 {@code synchronized}
 * —— 一条 60s 的慢 SQL 就是 60s 的 pin。载体线程池 parallelism 默认等于容器可见 CPU 数，
 * CPU limit 偏小时一两条慢 SQL 即可 pin 满，<b>全 JVM 虚拟线程停止调度</b>，连 WebClient 响应的
 * 续体都唤不醒。对账抽取是分钟级、上百次 DB 往返的批处理，放在请求线程上等于必然触发该故障。
 * 因此本类把任务派给一个<b>固定大小的平台线程池</b>（{@code new Thread(...)} 造出的就是平台线程），
 * 控制器只负责受理与立即返回。</p>
 *
 * <p>并发控制不依赖任何中间件（本项目不用 Redis / MQ）：同一 batchId 的在途标记放在
 * {@link ConcurrentHashMap#newKeySet()} 里，{@code add} 成功才受理，{@code finally} 里移除。
 * 单副本内足够；多副本场景由 recon-server 侧按批次分发保证只下发一次。</p>
 */
@Service
public class ReconExportService {

    private static final Logger log = LoggerFactory.getLogger(ReconExportService.class);

    /** 本源标识，EXP 行首 sourceType 段的取值，跨服务契约，NEVER 改。 */
    private static final String SOURCE = "ticket";

    private final ReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final int pageSize;

    private final ExecutorService executor;

    /** 在途批次标记，元素为 batchId。 */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * @param pageSize    EXP 每批查询行数，取自配置 {@code recon.export.page-size}
     * @param workerCount 抽取线程数，取自配置 {@code recon.export.worker}；默认 1 即串行，
     *                    刻意不放大——抽取是重 IO 的范围扫描，并发只会互相抢 Oracle 的 IO 与
     *                    Druid 连接，反而拖慢联机链路
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
     * 受理一次抽取指令：校验、去重、派给抽取线程池，<b>立即返回</b>。
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

    /**
     * 在抽取线程上按 fileTypes 逐类导出。
     *
     * <p>每类文件独立 try-with-resources：某一类抛异常时 {@link ReconPartSink#close()} 会自动向
     * recon-server 声明该类失败，本方法 catch 住后继续跑下一类。<b>NEVER 让一类的失败中断整轮</b>
     * —— EXP 与 BUS 是两份独立的对账文件，一类挂掉不该拖累另一类，否则重跑成本翻倍。</p>
     */
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
    /**
     * EXP 明细导出：keyset 游标逐批查询、逐行写入分片通道。
     *
     * <p><b>本方法及其调用链刻意不加 {@code @Transactional}</b>：每批一次查询、每条 SQL 自动提交。
     * 若把整轮抽取包进一个事务，事务时长等于抽取时长（分钟级），期间还夹着 {@code sink.write}
     * 触发的分片上送（HTTP 网络调用）—— 事务内发起 RPC 是本项目明令禁止的：连接被 Druid 的
     * {@code remove-abandoned-timeout} 判定为泄漏后强杀，{@code commit} 抛 connection closed，
     * 整轮白跑。抽取是纯只读，本来也不需要事务。</p>
     *
     * <p>行格式（12 段，字段顺序是跨服务契约，NEVER 调整）：
     * {@code sourceType|txnDate|txnTime|lineCode|stationCode|deviceId|cardType|cardId|trxType|trxAmount|orderNo|userId}。
     * {@code txnTime} 用 {@code HANDLE_DATE_TIME}（闸机交易时间，也是本文件的时间排序键）；
     * {@code orderNo} 取 {@code RESERVE1}（本表把该预留字段当支付交易订单号使用）；
     * {@code trxAmount} 是 {@code TRX_AMOUNT + OVERTIME_AMOUNT}，单位分。</p>
     *
     * <p>游标推进用<b>本批最后一行</b>的 {@code (HANDLE_DATE_TIME, ID)}，因为 SQL 的
     * {@code ORDER BY} 与游标条件严格对应；读不满一批即判定为最后一批并结束。</p>
     */
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
    /**
     * BUS 汇总导出：数据库内 GROUP BY 后一次查回，逐行写入。
     *
     * <p>行格式（5 段键 + 2 段度量，NEVER 调整顺序）：
     * {@code txnDate|lineCode|stationCode|deviceId|cardType|txnCount|txnAmount}。
     * {@code lineCode} 由 SQL 侧 LEFT JOIN {@code STATION_INFO} 得到，车站维表缺记录时为空串
     * ——由 {@link ReconRecord#line(Object...)} 统一把 null 转空串，
     * <b>NEVER 用车站代码前缀之类的规则去猜线路</b>，猜错等于把汇总账挂到错误线路上。</p>
     *
     * <p>同样不加 {@code @Transactional}，理由与 {@link #exportExp} 一致。</p>
     */
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

    /**
     * Oracle 数字列转 long。
     *
     * <p>{@code resultType=java.util.Map} 下 NUMBER 列一律是 {@link BigDecimal}，直接强转
     * {@code (Long)} 会抛 ClassCastException；本表金额列单位是分、都是整数，直接取
     * {@code longValue()}。本方法只服务本类，是私有实现细节，不是新建公共工具类。</p>
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

    /** Map 取字符串，null 保持 null 交由 {@link ReconRecord#line(Object...)} 统一转空串。 */
    private String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
