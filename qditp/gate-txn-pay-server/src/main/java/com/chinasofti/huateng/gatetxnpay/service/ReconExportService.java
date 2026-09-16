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

/**
 * 日终对账分片导出，本源标识 {@code gate-txn-pay}，负责甲方《ACC与ITP之间的文件》§一 的四类文件：
 * {@code ITP.EXP}（单边交易明细）、{@code ITP.PAY}（统计汇总）、{@code ITP.BUS}（商业优惠汇总）、
 * {@code ITP.DETAIL}（虚拟电子多日计次票明细）。
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
 * {@link ConcurrentHashMap} 里，{@code putIfAbsent} 成功才受理，{@code finally} 里移除。
 * 单副本内足够；多副本场景由 recon-server 侧按批次分发保证只下发一次。</p>
 *
 * <p><b>本类所有导出方法及其调用链刻意不加 {@code @Transactional}</b>：每批一次查询、每条 SQL
 * 自动提交。若把整轮抽取包进一个事务，事务时长等于抽取时长（分钟级），期间还夹着
 * {@code sink.write} 触发的分片上送（HTTP 网络调用）—— 事务内发起 RPC 是本项目明令禁止的：
 * 连接被 Druid 的 {@code remove-abandoned-timeout} 判定为泄漏后强杀，{@code commit} 抛
 * connection closed，整轮白跑。抽取是纯只读，本来也不需要事务。</p>
 */
@Service
public class ReconExportService {

    private static final Logger log = LoggerFactory.getLogger(ReconExportService.class);

    /** 本源标识，跨服务契约字段值，NEVER 改。 */
    private static final String SOURCE = "gate-txn-pay";

    /** PAY 行总段数：5 段键 + 16 段度量，甲方 §一(2) 明确列出，NEVER 增减。 */
    private static final int PAY_FIELD_COUNT = 21;

    /** PAY 行「过闸笔数」下标（甲方第 12 段，0 基下标 11），紧邻的下一段是「过闸金额」。 */
    private static final int PAY_IDX_GATE_COUNT = 11;

    /** PAY 行「单边交易笔数」下标（甲方第 18 段，0 基下标 17），紧邻的下一段是「单边交易金额」。 */
    private static final int PAY_IDX_EXP_COUNT = 17;

    /** DETAIL 行「交易类型」的固定取值。甲方原文：超时的行程在对账文件中类型为「出站」。 */
    private static final String DETAIL_TXN_TYPE_EXIT = "出站";

    private final ReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final int pageSize;

    private final ExecutorService executor;

    /** 在途批次标记，key 为 batchId。 */
    private final ConcurrentHashMap<String, Boolean> running = new ConcurrentHashMap<>();

    /**
     * @param pageSize     明细类（EXP / DETAIL）每批查询行数，取自配置 {@code recon.export.page-size}
     * @param workerCount  抽取线程数，取自配置 {@code recon.export.worker}；默认 1 即串行，
     *                     刻意不放大——抽取是重 IO 的全表扫描，并发只会互相抢 Oracle 的 IO 与
     *                     Druid 连接，反而拖慢联机链路
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

    /**
     * 在抽取线程上按 fileTypes 逐类导出。
     *
     * <p>每类文件独立 try-with-resources：某一类抛异常时 {@link ReconPartSink#close()} 会自动向
     * recon-server 声明该类失败，本方法 catch 住后继续跑下一类。<b>NEVER 让一类的失败中断整轮</b>
     * —— 四类是四份独立的对账文件，一类挂掉不该拖累另三类，否则重跑成本翻倍。</p>
     */
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

    /**
     * EXP 单边交易明细导出：keyset 游标逐批查询、逐行写入分片通道。
     *
     * <p>行格式（甲方 §一(1)，13 段，顺序即甲方顺序，NEVER 调整）：
     * {@code 逻辑卡号|票卡交易序列号|订单金额|实际扣款金额|优惠金额|进站设备编码|进站时间|
     * 出站处理设备类型|出站设备编码|出站时间|订单异常类型|支付方式|交易日期}。</p>
     *
     * <p>三处本表无对应数据、按空值输出：
     * <ul>
     *   <li><b>进站设备编码</b>：本表只有一个 {@code DEVICE_ID}，语义是出站设备，
     *       NEVER 拿它同时填进站设备，那会让 ACC 侧把出站闸机当成进站闸机对账。</li>
     *   <li><b>出站处理设备类型</b>：本表无该列。</li>
     *   <li><b>优惠金额</b>：固定 {@code 0}。本表唯一形似优惠的列 {@code DISCOUNT_LEVEL_AMT}
     *       语义是「命中的累计金额门槛」（见 {@code GateTxnPay.java:54}），不是优惠额本身，
     *       且生产数据大面积为 NULL，属不可靠列，取值口径待甲方确认。</li>
     * </ul>
     * </p>
     *
     * <p><b>实际扣款金额同样取 {@code TOTAL_AMOUNT}</b>：本表没有独立的实收列，
     * 「应扣」与「实扣」在库里是同一个值。两段同值是有意的，不是复制粘贴错误。</p>
     *
     * <p><b>订单异常类型原样输出，不做任何映射。</b>甲方定义 1~15
     * （1 单边账(入站) / 2 单边账(出站) / 3 单边入站人工处理单 / 4 单边出站人工处理单 /
     * 5 乘客自主补进站 / 6 乘客自主补出站 / 7 TVM 补币找零不足 / 8 TVM 卡票 /
     * 9 TVM 或 BOM 发售无效票 / 10 闸门无用 / 11 无票出闸 / 12 人为单程票无效 /
     * 13 非人为单程票无效 / 14 储值票无效 / 15 其他情况），
     * 但本表 {@code ORDER_EXP_TYPE} 的取值口径与之不一致：
     * {@code scripts/20260818_if8a_schema_migration.sql:28} 的列注释写的是
     * 「0 正常, 1 单边账(入), 2 单边账(出), 3 单边入站人工, 4 单边出站人工, 5 双段计费超时」
     * —— 前 4 个能对上，第 5 个甲方是「乘客自主补进站」、我方是「双段计费超时」，
     * 且我方多出一个「0 正常」而甲方无 0。<b>映射关系待甲方确认，确认前 NEVER 自行折算</b>：
     * 猜错会把超时行程报成自主补站，账目性质完全变了。</p>
     */
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

    /**
     * PAY 统计汇总导出：<b>两条库内 GROUP BY，各自输出一行</b>。
     *
     * <p>行格式（甲方 §一(2)，21 段 = 5 段键 + 16 段度量，NEVER 调整顺序）：
     * {@code 日期|线路|车站|设备编号|支付方式|BOM或TVM发售笔数|BOM或TVM发售金额|
     * BOM或TVM充值笔数|BOM或TVM充值金额|旅游票张数|旅游票金额|过闸笔数|过闸金额|
     * APP购票笔数|APP购票金额|BOM行政处理笔数|BOM行政处理金额|单边交易笔数|单边交易金额|
     * BOM处理笔数|BOM处理金额}。</p>
     *
     * <p>本模块只有能力填其中两组度量：<b>过闸（第 12、13 段）</b>与
     * <b>单边交易（第 18、19 段）</b>，其余 14 段一律写字面 {@code 0}。
     * 为此走两条 SQL：过闸组按 {@code DEBIT_STATUS='SUCCESS'} 聚合，单边组按
     * {@code ORDER_EXP_TYPE} 非空非空格聚合。<b>Java 侧对两个结果集各输出一行，
     * 彼此把对方那两段写 0</b>。</p>
     *
     * <p>同键出两行是安全的：recon-server 侧 {@code ReconFileGenerationService} 对汇总文件按
     * 键字段做二次累加，同键的两行会被合并，而每段度量在两行里只有一行非 0，
     * 相加即等于「同一行里各填各段」。<b>NEVER 为了「一行一键」改成 Java 侧先按键 join 两个结果集</b>
     * —— 那要把两个聚合全量拉进内存做 map 合并，收益是零、内存与出错面都变大。</p>
     *
     * <p><b>线路段（第 2 段）本模块一律留空</b>，2026-09-16 起改由 recon-server 在聚合完成、
     * 写文件之前按车站码统一补齐（见该模块的 {@code ReconStationMapper} 与
     * {@code recon.line-backfill.*} 配置）。因此本模块的两条 PAY 汇总 SQL 已删掉原先的
     * {@code LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.OUT_STATION} 与 {@code S.LINE_CODE}。
     * 收口的理由：线路是车站的函数，原先四个源各写一遍这个 join，等于「线路怎么取」有四份副本；
     * 而 {@code STATION_INFO} 属车站 / 参数域、owner 不是本模块，少一处跨域直连就少一处破例。
     * <b>NEVER 把 join 加回来</b>：recon-server 侧是无条件覆盖，加回来不改变产出、只让口径重新分叉。
     * <b>NEVER 改成用车站代码前 2 位推线路</b>——实测前 2 位恰好等于线路号是编码巧合，不是契约；
     * 这条约束在收口后依然成立，只是执行点搬到了 recon-server。</p>
     */
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
     * <p>笔数与金额在甲方格式里总是相邻两段（过闸 12/13、单边 18/19），因此只需传笔数下标，
     * 金额固定写在它的下一段。</p>
     *
     * @param row        聚合结果行，含 TXN_DATE / OUT_STATION / DEVICE_ID / SIGN_CHANNEL_CODE
     *                   （**没有 LINE_CODE**，线路段由 recon-server 按车站码补齐）
     * @param countIndex 笔数所在的 0 基下标
     * @param count      笔数
     * @param amount     金额，单位分
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

    /**
     * BUS 商业优惠汇总导出：一条库内 GROUP BY，逐行写入。
     *
     * <p>行格式（甲方 §一(3)，<b>只有 4 段</b> = 1 段键 + 3 段度量）：
     * {@code 日期|对账金额|付款金额|优惠金额}。这里没有线路、车站、设备、支付方式段，
     * <b>NEVER 照搬 PAY 的 5 段键</b>。</p>
     *
     * <p>对账金额与付款金额都取 {@code SUM(TOTAL_AMOUNT)}：本表没有独立的实收列。
     * 优惠金额固定 {@code 0}，理由同 {@link #exportExp} 的说明（{@code DISCOUNT_LEVEL_AMT}
     * 语义是累计金额门槛而非优惠额，且大面积为 NULL），待甲方确认后再接入。</p>
     */
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

    /**
     * DETAIL 虚拟电子多日计次票明细导出：keyset 游标逐批查询、逐行写入。
     *
     * <p>行格式（甲方 §一(4)，7 段，NEVER 调整顺序）：
     * {@code 运营日期|交易类型|逻辑卡号|交易日期时间|交易金额|当前车站名称|设备编码}。</p>
     *
     * <p>本模块只出<b>超时费</b>那一部分。甲方原文：多日计次票正常交易不进行对账，只对车票购买的
     * 交易；产生超时费的行程会针对超时费用进行对账；超时的行程在对账文件中类型为「出站」。
     * 因此这里交易类型固定 {@link #DETAIL_TXN_TYPE_EXIT}，交易金额取 {@code OVERTIME_AMOUNT}
     * 而不是 {@code TOTAL_AMOUNT} —— 对账的是超时费本身，报全额等于重复计账。
     * 「发售」类型的行（车票购买，甲方要求车站与设备编码传空）由 daily-ticket-server 负责，
     * <b>NEVER 在本模块产出</b>。</p>
     *
     * <p>卡类型判定用 {@code CARD_TYPE IN ('0445','0446','0447','0448')}，取值核对自 model 模块
     * {@code CardTypeMapping.java:54} 的 {@code DAY_TICKET_ISSUE_TYPES} 与 {@code CardTypeCodeEnum}。
     * 甲方在同一份文档 §二 里说 ACC 侧是「根据 SIGN_CHANNEL_CODE 字段进行票种区分」，
     * 那是 ACC 侧的做法；本表 {@code SIGN_CHANNEL_CODE} 是签约支付通道、不是票种，
     * <b>NEVER 拿它判日票</b>。</p>
     */
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
