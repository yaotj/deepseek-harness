package com.chinasofti.huateng.collectpay.service;

import com.chinasofti.huateng.collectpay.mapper.ReconExportMapper;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日终对账分片导出，本源标识 {@code collect-pay}，<b>只负责 ITP.PAY 与 ITP.BUS 两类文件</b>。
 *
 * <p><b>ITP.DETAIL 是虚拟电子多日计次票专用文件，本模块 NEVER 产出。</b>
 * 甲方规格《ACC与ITP之间的文件》第一章第 4 节明确 DETAIL 的口径是「虚拟电子多日计次票的明细和
 * 总金额，多日计次票正常交易不对账、只对车票购买与超时费」，与 TVM / BOM 的非现金收款毫无关系，
 * 已改由 gate-txn-pay 与 daily-ticket 负责。本类收到 {@code DETAIL} 或 {@code EXP} 指令时只记
 * warn 并跳过，<b>NEVER 拿本模块的四张收款表去凑 DETAIL 行</b>——凑出来的是错账文件，
 * 而纯文本分片没有 schema，下游读不出异常。</p>
 *
 * <p>覆盖四张收款主表：{@code TBL_TVM_ORDER_PAY}（TVM 扫码购票）、
 * {@code TBL_TVM_ORDER_TOPUP}（TVM 扫码充值）、{@code TBL_TVM_APP_ORDER}（APP 在线购票）、
 * {@code TBL_BOM_ORDER_PAY}（BOM 非现金收款）。四张表只导 STATUS / PAY_STATUS 为
 * {@code '1'} 的成功单（{@code ItpStatusEnum}）。</p>
 *
 * <p><b>抽取绝不能跑在请求线程上。</b>全服务默认 {@code spring.threads.virtual.enabled=true}
 * （{@code resource/micro/web/src/main/resources/web.properties:6}），Tomcat 处理线程是虚拟线程。
 * JDK 21 未落地 JEP 491，虚拟线程在 {@code synchronized} 内阻塞会 <b>pin 住载体线程</b>，
 * 而 ojdbc8 的 {@code PhysicalConnection} / {@code OracleStatement} 大量方法是 {@code synchronized}
 * —— 一条 60s 的慢 SQL 就是 60s 的 pin。载体线程池 parallelism 默认等于容器可见 CPU 数，
 * CPU limit 偏小时一两条慢 SQL 即可 pin 满，<b>全 JVM 虚拟线程停止调度</b>，连 WebClient 响应的
 * 续体都唤不醒。对账抽取是区间全扫级别的批处理，放在请求线程上等于必然触发该故障。
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

    /** 本源标识，跨服务契约的第一段字段值，NEVER 改。 */
    private static final String SOURCE = "collect-pay";

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

    private final ReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final ExecutorService executor;

    /** 在途批次标记。 */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * @param workerCount 抽取线程数，取自配置 {@code recon.export.worker}；默认 1 即串行，
     *                    刻意不放大——抽取是重 IO 的区间扫描，并发只会互相抢 Oracle 的 IO 与
     *                    Druid 连接，反而拖慢联机链路
     */
    public ReconExportService(ReconExportMapper reconExportMapper,
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
     * —— PAY 与 BUS 是两份独立的对账文件，一类挂掉不该拖累另一类，否则重跑成本翻倍。</p>
     *
     * <p><b>本方法及其调用链刻意不加 {@code @Transactional}</b>：每类一次查询、每条 SQL 自动提交。
     * 若把整轮抽取包进一个事务，事务时长等于抽取时长，期间还夹着 {@code sink.write}
     * 触发的分片上送（HTTP 网络调用）—— 事务内发起 RPC 是本项目明令禁止的：连接被 Druid 的
     * {@code remove-abandoned-timeout} 判定为泄漏后强杀，{@code commit} 抛 connection closed，
     * 整轮白跑。抽取是纯只读，本来也不需要事务。</p>
     */
    private void runExport(ReconExportReqDTO request) {
        List<String> failures = new ArrayList<>();
        for (String fileType : request.getFileTypes()) {
            ReconFileTypeEnum type = ReconFileTypeEnum.of(fileType);
            try {
                switch (type) {
                    case PAY -> exportPay(request);
                    case BUS -> exportBus(request);
                    default -> log.warn("collect-pay 不负责该对账文件类型，已跳过 batchId={}, fileType={}",
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
     * ITP.PAY 统计汇总导出：四条库内 GROUP BY 的结果依次写入同一个 sink。
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
     * <p>本模块只填三组，每行只有一组非 0，其余 13 段度量写字面 {@code 0}：</p>
     * <ul>
     *   <li><b>BOM/TVM 发售</b>（5 / 6）← {@code TBL_TVM_ORDER_PAY} 与 {@code TBL_BOM_ORDER_PAY}；</li>
     *   <li><b>BOM/TVM 充值</b>（7 / 8）← {@code TBL_TVM_ORDER_TOPUP}；</li>
     *   <li><b>APP 购票</b>（13 / 14）← {@code TBL_TVM_APP_ORDER}。</li>
     * </ul>
     *
     * <p><b>旅游票（9 / 10）与过闸（11 / 12）本模块没有对应业务</b>，单边交易（17 / 18）属 ITP.EXP
     * 口径、也不在本模块。<b>BOM 行政处理（15 / 16）与 BOM 处理（19 / 20）两组，本模块无法可靠
     * 识别，一律填 0，待甲方明确判据后再补</b>——{@code TBL_BOM_ORDER_PAY.TRANS_TYPE} 的取值在仓库
     * 内三处证据互相矛盾（{@code BomNoCashOrder} 注释清单里根本没有「发售」、
     * {@code BomBusinessCodeEnum} 的 {@code 01} 与 {@code 22} 描述都写「充值」、
     * {@code BomOrderServiceImpl:950} 又把发售硬编码成 {@code 01}），详见
     * {@link ReconExportMapper#selectBomPayPaySummary}。同理整张 BOM 表都计入「发售」组，
     * 不按 {@code TRANS_TYPE} 拆分，这是有意的降级：<b>宁可标注也不凭猜写取值</b>。</p>
     *
     * <p><b>第 1 段线路：本模块一律不出，四组都传 {@code null}。</b>
     * 2026-09-16 起线路段改由 recon-server 在聚合完成、写文件之前按车站码统一补齐
     * （见该模块的 {@code ReconStationMapper} 与 {@code recon.line-backfill.*} 配置），因此
     * TVM 购票与 APP 购票这两条原先靠
     * {@code LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.IN_STATION_CODE} 取 {@code LINE_CODE}
     * 的写法已整段删除。收口的理由：线路是车站的函数，原先四个源各写一遍这个 join，
     * 等于「线路怎么取」有四份副本；而 {@code STATION_INFO} 属车站 / 参数域、owner 不是本模块。
     * <b>NEVER 把 join 加回来</b>：recon-server 侧是无条件覆盖，加回来不改变产出、只让口径重新分叉。
     * <b>NEVER 用车站码前缀之类的规则去猜线路</b>，猜错等于把汇总账挂到错误线路上；
     * 这条约束在收口后依然成立，只是执行点搬到了 recon-server。</p>
     *
     * <p><b>连带结论：TVM 充值与 BOM 两组的线路段仍然是空的，但成因变了。</b>
     * 收口后 recon-server 是按「车站段」反查维表补线路的，而
     * {@code TBL_TVM_ORDER_TOPUP} / {@code TBL_BOM_ORDER_PAY} 连车站码列都没有、车站段本身就空，
     * 于是线路段也补不出来。**空的成因从「本模块拿不到车站码」变成「这两组没有车站段」，结论不变**；
     * 待甲方补数据源后，本模块只要把车站段填上，线路段就会由 recon-server 自动补齐，
     * <b>届时不需要在本模块加任何 join</b>。
     * 四张表的同键分组可能重复出现（例如同一天同一设备同一通道，购票与充值各一行），
     * 这是<b>允许的</b>：recon-server 按前 5 段键做二次聚合、对 16 个度量列逐列累加。</p>
     */
    private void exportPay(ReconExportReqDTO request) {
        String windowStart = request.getWindowStartDashed();
        String windowEnd = request.getWindowEndDashed();
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.PAY)) {
            for (Map<String, Object> row : reconExportMapper.selectTvmPayPaySummary(windowStart, windowEnd)) {
                writePayRow(sink, row, null, toStr(row.get("IN_STATION_CODE")),
                        toStr(row.get("DEVICE_ID")), toStr(row.get("CHANNEL")), PAY_IDX_SALE_COUNT);
            }
            for (Map<String, Object> row : reconExportMapper.selectBomPayPaySummary(windowStart, windowEnd)) {
                writePayRow(sink, row, null, null,
                        toStr(row.get("DEVICE_ID")), toStr(row.get("CHANNEL")), PAY_IDX_SALE_COUNT);
            }
            for (Map<String, Object> row : reconExportMapper.selectTvmTopupPaySummary(windowStart, windowEnd)) {
                writePayRow(sink, row, null, null,
                        toStr(row.get("DEVICE_ID")), toStr(row.get("CHANNEL")), PAY_IDX_TOPUP_COUNT);
            }
            for (Map<String, Object> row : reconExportMapper.selectAppOrderPaySummary(windowStart, windowEnd)) {
                writePayRow(sink, row, null, toStr(row.get("IN_STATION_CODE")),
                        null, toStr(row.get("PAY_CHANNEL_CODE")), PAY_IDX_APP_COUNT);
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
     * @param row        聚合结果行，需含 {@code TXN_DATE} / {@code TXN_COUNT} / {@code TXN_AMOUNT}
     * @param lineCode   线路段。**本模块四组一律传 {@code null}**：线路由 recon-server 按车站码统一补齐
     *                   （见本类 {@code exportPay} 的说明）。形参保留是为了让 21 段的段位在签名上仍然可见，
     *                   NEVER 在这里塞任何自己算出来的线路值
     * @param stationCode 车站段，该表无该列时传 {@code null}，由 {@link ReconRecord} 统一转空串
     * @param deviceId   设备编号段，该表无该列时传 {@code null}
     * @param payChannel 支付方式段
     * @param countIndex 本组「笔数」在 21 段里的下标，金额固定为它 + 1
     */
    private void writePayRow(ReconPartSink sink, Map<String, Object> row, String lineCode, String stationCode,
                             String deviceId, String payChannel, int countIndex) {
        long count = toLong(row.get("TXN_COUNT"));
        long amount = toLong(row.get("TXN_AMOUNT"));
        Object[] fields = new Object[PAY_FIELD_COUNT];
        Arrays.fill(fields, PAY_METRIC_BEGIN, PAY_FIELD_COUNT, 0L);
        fields[0] = toStr(row.get("TXN_DATE"));
        fields[1] = lineCode;
        fields[2] = stationCode;
        fields[3] = deviceId;
        fields[4] = payChannel;
        fields[countIndex] = count;
        fields[countIndex + 1] = amount;
        sink.write(ReconRecord.line(fields), amount);
    }
    /**
     * ITP.BUS 商业优惠汇总导出：<b>只有 4 段</b> = 1 段键（日期）+ 3 段度量。
     *
     * <p>行格式（字段顺序来自甲方规格，NEVER 调整）：
     * {@code 日期|对账金额|付款金额|优惠金额}。</p>
     *
     * <p>本模块的三个度量取值：<b>对账金额与付款金额都取该表金额列之和，优惠金额恒 0</b>。
     * 优惠段填 0 是核对过 DDL 的结论——{@code collect-pay-server/sql.txt} 里
     * {@code TBL_TVM_ORDER_PAY}、{@code TBL_TVM_ORDER_TOPUP}、{@code TBL_TVM_APP_ORDER}、
     * {@code TBL_BOM_ORDER_PAY} 四张表<b>都没有任何优惠 / 折扣金额列</b>，
     * NEVER 拿票价与实付之差去推算：{@code TICKET_PRICE * TICKET_NUM} 与实付的差额可能来自
     * 分单、退款或脏数据，算出来的不是优惠。</p>
     *
     * <p>四张表各一条按日 GROUP BY，结果依次写入同一个 BUS sink，<b>同一天出现多行是允许的</b>：
     * recon-server 按 1 段键（日期）做二次聚合、把三个度量逐列累加合并。</p>
     */
    private void exportBus(ReconExportReqDTO request) {
        String windowStart = request.getWindowStartDashed();
        String windowEnd = request.getWindowEndDashed();
        try (ReconPartSink sink = uploader.open(request.getBatchId(), SOURCE, ReconFileTypeEnum.BUS)) {
            for (Map<String, Object> row : reconExportMapper.selectTvmPayBusSummary(windowStart, windowEnd)) {
                writeBusRow(sink, row);
            }
            for (Map<String, Object> row : reconExportMapper.selectTvmTopupBusSummary(windowStart, windowEnd)) {
                writeBusRow(sink, row);
            }
            for (Map<String, Object> row : reconExportMapper.selectAppOrderBusSummary(windowStart, windowEnd)) {
                writeBusRow(sink, row);
            }
            for (Map<String, Object> row : reconExportMapper.selectBomPayBusSummary(windowStart, windowEnd)) {
                writeBusRow(sink, row);
            }
            sink.commit();
            log.info("BUS 抽取完成 batchId={}, parts={}, records={}, amount={}",
                    request.getBatchId(), sink.getUploadedParts(), sink.getTotalRecords(), sink.getTotalAmount());
        }
    }

    /** 写一行 ITP.BUS：日期 + 对账金额 + 付款金额 + 优惠金额（本模块恒 0）。 */
    private void writeBusRow(ReconPartSink sink, Map<String, Object> row) {
        long amount = toLong(row.get("TXN_AMOUNT"));
        String line = ReconRecord.line(toStr(row.get("TXN_DATE")), amount, amount, 0L);
        sink.write(line, amount);
    }
    /**
     * Oracle 数字 / 字符串金额列转 long（单位分）。
     *
     * <p>{@code resultType=java.util.Map} 下 {@code COUNT} 与 {@code TO_NUMBER} 的结果一律是
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
}
