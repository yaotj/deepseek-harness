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

/**
 * 日终对账分片导出，本源标识 {@code face-pay}，<b>只负责 ITP.PAY 与 ITP.BUS 两类文件</b>。
 *
 * <p>本类是 recon-server 的第 4 个抽取源（前三个是 gate-txn-pay / collect-pay / daily-ticket）。
 * 骨架逐字沿用 {@code collect-pay-server} 的同名类：受理即返回、抽取跑在固定大小的<b>平台</b>
 * 线程池上、每类文件独立 try-with-resources、一类失败不中断另一类。<b>NEVER 把抽取放回请求线程</b>：
 * 全服务默认 {@code spring.threads.virtual.enabled=true}，请求线程是虚拟线程，
 * 而 ojdbc8 大量方法是 {@code synchronized}，区间全扫级别的慢 SQL 会 pin 住载体线程，
 * CPU limit 偏小时一两条即可 pin 满、全 JVM 虚拟线程停止调度。</p>
 *
 * <p><b>与 collect-pay 那个源的口径差异（评审与改动时 MUST 先看这段）：</b></p>
 * <ul>
 *   <li><b>数据来源是 {@code F2F_ORDER} join {@code F2F_PAYMENT}</b>，不是四张旧表。
 *       collect-pay 那边读的 {@code TBL_TVM_*} / {@code TBL_BOM_ORDER_PAY} 已于 2026-09-15 停止
 *       接收新流量（ADR-D85），<b>但旧表里的历史数据仍要由那个源导出、NEVER 停掉它</b>；
 *       本源只导 {@code F2F_*} 里的新单。两个源同时在册、各导自己那部分，账才完整。</li>
 *   <li><b>「BOM 行政处理」（idx 15/16）与「BOM 处理」（idx 19/20）本源能算出来</b>，
 *       collect-pay 那边这两组恒 0。理由见 {@link F2fReconExportMapper#selectBomAdminPaySummary}：
 *       {@code F2F_ORDER} 的 {@code BIZ_TYPE} 与 {@code TRANS_TYPE} 正交，
 *       {@code TRANS_TYPE='42'} 的行政处理语义唯一。</li>
 *   <li><b>本源恒 0 的是另外三组</b>：旅游票（9/10，归 daily-ticket）、过闸（11/12，归 gate-txn-pay）、
 *       单边交易（17/18，属 ITP.EXP 口径、本模块无判据）。BUS 的优惠段同样恒 0
 *       （{@code F2F_*} 无任何优惠 / 折扣列，NEVER 拿票价与实付之差去推算）。</li>
 * </ul>
 *
 * <p>并发控制不依赖任何中间件（本项目不用 Redis / MQ）：同一 batchId 的在途标记放在
 * {@link ConcurrentHashMap#newKeySet()} 里，{@code add} 成功才受理，{@code finally} 里移除。
 * 单副本内足够；本模块<b>本来就 MUST 单副本</b>（7 个 {@code @Scheduled} 无分布式锁）。</p>
 */
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
     *                    刻意不放大——抽取是重 IO 的区间扫描，并发只会互相抢 Oracle 的 IO 与
     *                    Druid 连接，反而拖慢联机链路
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
     * —— PAY 与 BUS 是两份独立的对账文件，一类挂掉不该拖累另一类。</p>
     *
     * <p><b>本方法及其调用链刻意不加 {@code @Transactional}</b>：抽取是纯只读，且链路里夹着
     * {@code sink.write} 触发的分片上送（HTTP）——事务内发起 RPC 是本项目明令禁止的。</p>
     */
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

    /**
     * 两个漏账探针，只记日志、不影响抽取结果。
     *
     * <p>五条汇总 select 的 {@code BIZ_TYPE} 是白名单、窗口列是 {@code O.PAID_TMS}，因此有两类
     * 「有钱但不进任何一段度量」的行会被<b>静默丢掉</b>——纯文本对账文件没有 schema，下游读不出异常。
     * 这里把它们各变成一条 WARN。<b>NEVER 删</b>；也 NEVER 改成抛异常中断抽取：漏几笔要人工核，
     * 但整份文件不出更严重。</p>
     */
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

    /**
     * ITP.PAY 统计汇总导出：五条库内 GROUP BY 的结果依次写入同一个 sink。
     *
     * <p>行格式（21 段 = 前 5 段键 + 后 16 段度量，字段顺序来自甲方规格，NEVER 调整）：</p>
     * <pre>
     * 键   0 日期 | 1 线路 | 2 车站 | 3 设备编号 | 4 支付方式
     * 度量 5 BOM/TVM发售笔数  | 6 BOM/TVM发售金额
     *      7 BOM/TVM充值笔数  | 8 BOM/TVM充值金额
     *      9 旅游票张数       | 10 旅游票金额
     *      11 过闸笔数        | 12 过闸金额
     *      13 APP购票笔数     | 14 APP购票金额
     *      15 BOM行政处理笔数 | 16 BOM行政处理金额
     *      17 单边交易笔数    | 18 单边交易金额
     *      19 BOM处理笔数     | 20 BOM处理金额
     * </pre>
     *
     * <p>本源填五组、每行只有一组非 0，其余 11 段度量写字面 {@code 0}：发售（5/6）←
     * {@code BIZ_TYPE='01'} 且 {@code CHANNEL IN ('02','03')}；充值（7/8）← {@code BIZ_TYPE='02'}；
     * APP 购票（13/14）← {@code BIZ_TYPE='01'} 且 {@code CHANNEL='01'}；
     * BOM 行政处理（15/16）← {@code BIZ_TYPE='04'} 且 {@code TRANS_TYPE='42'}；
     * BOM 处理（19/20）← {@code BIZ_TYPE='04'} 且 {@code TRANS_TYPE} 非 42。</p>
     *
     * <p><b>恒 0 的三组</b>：旅游票（9/10）归 daily-ticket、过闸（11/12）归 gate-txn-pay、
     * 单边交易（17/18）属 ITP.EXP 口径且本模块无判据。<b>NEVER 拿 {@code F2F_*} 去凑这三组</b>
     * ——凑出来的是错账，而纯文本分片没有 schema、下游读不出异常。</p>
     *
     * <p>同键分组在多组间可能重复出现（例如同一天同一设备同一支付方式，购票与充值各一行），
     * 这是<b>允许的</b>：recon-server 按前 5 段键做二次聚合、对 16 个度量列逐列累加。</p>
     */
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
     * <p>先把 16 段度量全部填 {@code 0L}（写进文件就是字面 {@code 0}），再按组下标覆盖两段。
     * <b>NEVER 只拼「键 + 本组两段」这种短行</b>——段数不足时 recon-server 按下标取度量列会整体
     * 错位，而纯文本没有 schema、不会报错，只会静默出错账。</p>
     *
     * <p>五条 select 的别名完全一致（{@code TXN_DATE} / {@code STATION_CODE} / {@code DEVICE_ID} /
     * {@code PAY_CHANNEL_CODE} / {@code TXN_COUNT} / {@code TXN_AMOUNT}），因此键段统一在这里取，
     * 调用方只需给出组下标。<b>改任一条 select 的列别名 MUST 同步本方法。</b>
     * 取不到的段（例如 APP 单没有 {@code DEVICE_ID}）是 {@code null}，
     * 由 {@link ReconRecord#line(Object...)} 统一转空串。</p>
     *
     * <p><b>第 2 段（线路）本模块一律留空</b>：线路段已整体收口到 recon-server，由它在聚合后
     * 按车站段反查 {@code STATION_INFO} 统一补齐（无条件覆盖，与本模块送不送值无关）。因此五条
     * select 都不再出 {@code LINE_CODE}，<b>NEVER 在本方法里塞线路值、NEVER 把那个 join 加回
     * mapper、更 NEVER 用车站码前 2 位推线路</b>。</p>
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

    /**
     * ITP.BUS 商业优惠汇总导出：<b>只有 4 段</b> = 1 段键（日期）+ 3 段度量，
     * 行格式 {@code 日期|对账金额|付款金额|优惠金额}（字段顺序来自甲方规格，NEVER 调整）。
     *
     * <p>对账金额与付款金额都取窗口内成功支付的 {@code PAY_AMOUNT} 之和；<b>优惠金额恒 0</b>
     * ——{@code F2F_ORDER} / {@code F2F_PAYMENT} 都没有任何优惠 / 折扣金额列，
     * NEVER 拿 {@code ORDER_AMOUNT} 与 {@code PAY_AMOUNT} 之差去推算：那个差额可能来自部分退款、
     * 聚合码场景或脏数据，算出来的不是优惠。</p>
     *
     * <p>本源只有一条按日 GROUP BY（口径与 PAY 五组的并集一致），因此正常情况下每天一行；
     * 同一天出现多行也是<b>允许的</b>，recon-server 按 1 段键做二次聚合、三个度量逐列累加。</p>
     */
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

    /**
     * Oracle 数字列转 long（单位分）。
     *
     * <p>{@code resultType=java.util.Map} 下 {@code COUNT} 与 {@code SUM} 的结果一律是
     * {@link BigDecimal}，直接强转 {@code (Long)} 会抛 ClassCastException。本方法只服务本类，
     * 是私有实现细节，不是新建公共工具类。</p>
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

    /** 探针 {@code COUNT(*)} 理论上不会返 null，仍兜一层，NEVER 直接拆箱。 */
    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
