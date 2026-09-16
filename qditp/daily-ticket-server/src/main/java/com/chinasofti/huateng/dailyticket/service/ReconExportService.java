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

/**
 * 日终对账分片导出（来源标识 {@code daily-ticket}，负责 DETAIL 与 PAY 两类文件）。
 *
 * <p>两类文件的段序与取值口径来自甲方 {@code docs/接口规范文档/ACC与ITP之间的文件.docx} §一
 * 「对账文件」，DETAIL 对应「（4）虚拟电子多日计次票文件」、PAY 对应「（2）统计汇总文件」，
 * 与 {@link ReconFileTypeEnum} 的类注释一一对应。**NEVER 自行重排字段或改段数** —— 分片是纯文本、
 * 无 schema，错位不报错，只会静默出错账。</p>
 *
 * <p><b>本模块只产出 DETAIL 的「车票购买（发售）」行。</b>甲方规格里 DETAIL 还有另一类行：
 * 「超时的行程在对账文件中类型为出站，费用为线网最高票价或者 1」。那部分数据在闸机出站扣费链路上，
 * **由 gate-txn-pay-server 负责产出，本模块 NEVER 产出** —— 本模块 5 张表既没有进出站信息也没有
 * 超时费用，硬凑只能凭空造数。两个源各写各的行，由 recon-server 拼成同一个 ITP.DETAIL 文件。</p>
 *
 * <p>受理与抽取严格分离：{@link #submit(ReconExportReqDTO)} 只做校验 + 入队，抽取跑在本类自己的
 * **固定大小平台线程池**上。</p>
 *
 * <p>**NEVER 在请求线程上跑抽取**：全服务 {@code spring.threads.virtual.enabled=true}，请求线程是
 * 虚拟线程；JDK 21 未落地 JEP 491，虚拟线程在 {@code synchronized} 内阻塞会 pin 住载体线程，而
 * ojdbc8 的 {@code PhysicalConnection} / {@code OracleStatement} 大量方法是 {@code synchronized}。
 * 载体线程池 parallelism 默认等于容器可见 CPU 数，一两条慢 SQL 就能 pin 满，**全 JVM 虚拟线程停止
 * 调度**，连 WebClient 响应的续体都唤不醒（2026-08-26 生产事故形态）。抽取是「几百万行 × 阻塞 JDBC」，
 * 必须放在平台线程上，因此这里用 {@code Executors.newFixedThreadPool} 显式建平台线程，
 * NEVER 换成 {@code newVirtualThreadPerTaskExecutor} 或 {@code @Async} 默认执行器。</p>
 *
 * <p>并发控制不引入任何中间件（本项目不用 Redis / MQ）：同一 batchId 在途时用
 * {@link ConcurrentHashMap#newKeySet()} 标记并直接回绝重复指令，收尾在 finally 里移除。
 * recon-server 侧本身会超时补偿重推，回绝属限流、不是失败。</p>
 */
@Service
public class ReconExportService {

    /** 本源在对账体系里的来源标识，与 recon-server 注册的源名一致，NEVER 改。 */
    public static final String SOURCE = "daily-ticket";

    /**
     * DETAIL 第 2 段「交易类型」在车票购买场景下的固定取值，甲方原文即中文「发售」。
     *
     * <p>甲方另一类取值是「出站」（超时行程），那类行由 gate-txn-pay-server 产出，本模块不涉及。
     * 分片按 UTF-8 写出（见 {@code ReconPartSink}），**NEVER 改成拼音或英文缩写** —— 这是甲方
     * 文件内容契约的一部分，改了对方解析不出。</p>
     */
    private static final String TXN_TYPE_SALE = "发售";

    private static final Logger log = LoggerFactory.getLogger(ReconExportService.class);

    private final ReconExportMapper reconExportMapper;

    private final ReconPartUploader uploader;

    private final int pageSize;

    private final ExecutorService executor;

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
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
     * <p>方法上**故意不加 {@code @Transactional}**：抽取是长循环的只读扫描，包在事务里会让一个数据库
     * 事务横跨整段导出（分钟级），Druid 的 {@code remove-abandoned-timeout} 到点会强杀连接，
     * 抽取整体失败；且事务内还夹着向 recon-server 上送分片的 HTTP 调用，正好触碰
     * 「{@code @Transactional} 内 NEVER 发起 RPC」这条红线。这里每条 SQL 自动提交，读到哪算哪。</p>
     *
     * <p>某一类文件失败只影响该类：catch 住记 {@code log.error} 后继续下一类。未 {@code commit()} 就
     * {@code close()} 时 {@link ReconPartSink} 会自动向 recon-server 声明该类失败，因此 NEVER 自己
     * 吞掉异常又不留痕——那会让 recon-server 永远停在 EXPORTING 等不到收齐。</p>
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

    /**
     * DETAIL：虚拟电子多日计次票的「车票购买（发售）」明细，Keyset 游标翻页逐行写出。
     *
     * <p>行格式（甲方 7 段，段序即甲方原文表头）：
     * {@code 运营日期|交易类型|逻辑卡号|交易日期时间|交易金额|当前车站名称|设备编码}。</p>
     *
     * <p>第 2 段固定写中文串 {@link #TXN_TYPE_SALE}「发售」，第 6、7 段固定写空串 —— 甲方原文
     * 「车票购买的交易在对账文件中交易类型为发售，当前车站类型和设备编码传空」。**NEVER 拿订单来源
     * 或渠道去填第 6、7 段**：甲方要的是空，填了反而对不上。</p>
     *
     * <p>超时行程那类「出站」行不在这里，见类注释：由 gate-txn-pay-server 产出。</p>
     *
     * <p><b>票种范围待甲方确认，当前导出全部已付日票订单（有意降级，不是遗漏）。</b>甲方这个文件名义上
     * 专供「虚拟电子多日计次票」，但本模块三个候选判据逐一核对后都不可靠：
     * ①{@code DAILY_TICKET_INSTANCE.CODE_TICKET_TYPE} DDL 默认 {@code '0441'}
     * （{@code sql/daily-ticket-server-schema.sql:60}），激活时代码又无条件写
     * {@code CardTypeCodeEnum.QR_POSTPAID.getCode()} 即 {@code '0441'}
     * （{@code DailyTicketServiceImpl.java:591}），全表同值、零区分度；
     * ②{@code DAILY_TICKET_ORDER.CARD_TYPE} 原样落 APP 入参
     * （{@code DailyTicketServiceImpl.java:182} 与 {@code :1153}，入口只校验非空、没走
     * {@code CardTypeMapping.isSupportedAppCardType}），库里可能混着 APP 口径（含聚合桶 {@code 05}，
     * 一个值覆盖 {@code 0445}~{@code 0448}，本身分不出计次票）、ACC 两位口径与发卡口径三套编码；
     * ③{@code SHOW_TYPE} 同样原样落入参，全仓库无比较点、语义未定义。
     * 因此**宁可多导并标注，NEVER 凭猜写码值** —— 猜错的筛选条件会静默命中 0 行或漏掉整类票。
     * 收窄的前提是甲方给出码值口径，或订单侧改成落规范化后的发卡卡类型。</p>
     *
     * <p><b>旅游票子单也在本明细里，这是已知事实、不是遗漏。</b>旅游票（IF8A-70）按
     * {@code ticketCount} 拆成多条 {@code DAILY_TICKET_ORDER} 子单
     * （{@code PARENT_ORDER_NO} 指向 {@code TRAVEL_TICKET_ORDER.ORDER_NO}），每条子单各自走一次
     * 支付、各自回写 {@code PAY_STATUS='PAID'} 与 {@code PAY_DATE}，所以会被
     * {@code selectDetailPage} 的 {@code PAY_STATUS='PAID'} 条件全部捞进来。PAY 是按日期与支付
     * 方式的汇总、DETAIL 是逐笔明细，**两者并存不构成重复计账**。未决点：甲方是否要求 DETAIL
     * 只含独立日票（不含旅游票子单），规格原文里**没有依据**。若日后甲方明确要求排除，改法是在
     * {@code selectDetailPage} 的 WHERE 追加 {@code AND O.PARENT_ORDER_NO IS NULL}；
     * **当前无依据、不加**，NEVER 凭推测加这个条件 —— 加错等于让整类已售票在 ACC 侧凭空消失。</p>
     */
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

    /**
     * PAY：统计汇总，库内 GROUP BY 后一次查完逐行写出。
     *
     * <p>行格式（甲方 21 段 = 前 5 段键 + 后 16 段度量）：
     * {@code 日期|线路|车站|设备编号|支付方式|BOM/TVM发售笔数|BOM/TVM发售金额|BOM/TVM充值笔数|
     * BOM/TVM充值金额|旅游票张数(发售)|旅游票金额|过闸笔数|过闸金额|APP购票笔数|APP购票金额|
     * BOM行政处理笔数|BOM行政处理金额|单边交易笔数|单边交易金额|BOM处理笔数|BOM处理金额}。</p>
     *
     * <p><b>本模块只填「旅游票张数(发售) / 旅游票金额」这一组（0 基下标 9 / 10）</b>，其余 14 段度量
     * 一律写字面 {@code 0}。recon-server 按前 5 段键把各源的行累加成一行，写 0 的段自然由别的源填上。
     * **NEVER 把不负责的度量写成空串**：空串在 {@link ReconRecord#metric(String[], int)} 里虽然按 0
     * 处理，但段数与其他源不一致时会整行错位。</p>
     *
     * <p><b>数据取自旅游票子单 {@code DAILY_TICKET_ORDER}，不是主单 {@code TRAVEL_TICKET_ORDER}</b>
     * （2026-09-11 改口径）：主单只是聚合壳，插入后 {@code PAY_STATUS} 永远停在 {@code 'INIT'}
     * （全仓库对 {@code travelTicketOrderMapper} 只有 {@code insert}、没有 UPDATE），支付事实
     * 全在子单上。详细理由与判据见 {@code ReconExportMapper.selectTravelTicketPaySummary}。</p>
     *
     * <p><b>「张数」在新口径下就是 {@code COUNT(*)}</b>：子单表没有 {@code TICKET_COUNT} 列，
     * 且下单时按 {@code ticketCount} 循环拆单，一条子单恰好一张票，笔数即张数。
     * NEVER 换回 {@code SUM(TICKET_COUNT)}（那是主单的列）。</p>
     *
     * <p>键的第 5 段「支付方式」**已改为填实值** {@code PAY_CHANNEL_CODE}（子单上有该列，
     * 由 {@code requestPay} 落库），因此 SQL 的分组键含该列、一天可能返回多行。
     * 线路 / 车站 / 设备编号三段仍恒为空串：本模块 5 张表都没有对应列
     * （{@code DAILY_TICKET_ORDER}、{@code DAILY_TICKET_INSTANCE}、{@code DAILY_TICKET_PAY_LOG}、
     * {@code DAILY_TICKET_REFUND}、{@code TRAVEL_TICKET_ORDER} 已全量核对）。空串参与键的组成，
     * 因此这三段 NEVER 改成 null 或占位符，否则与其他源的键空间对不上；
     * 支付方式段取不到值时也会落成空串（{@link #toStr(Object)} 的 null 兜底），与旧行为一致。</p>
     *
     * <p><b>线路段（第 2 段）自 2026-09-16 起由 recon-server 统一补齐</b>：它在聚合完成、写文件之前
     * 按 <b>车站段</b> 反查 {@code STATION_INFO}，且 <b>无条件覆盖</b>本模块送来的值
     * （见 recon-server 的 {@code ReconStationMapper} 与 {@code recon.line-backfill.*}）。
     * 因此本模块这一段的语义变了：不再是「本模块没有线路列所以空」，而是「四个源一律不出线路」。
     * 由于本模块<b>连车站段也没有</b>，recon-server 反查不到、补出来仍是空串，
     * <b>文件内容与收口前逐字节一致</b>。将来若本模块补上了车站段，线路会自动跟着补齐，
     * <b>届时 NEVER 在本模块加 {@code STATION_INFO} 的 join 自己算线路</b>。</p>
     */
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

    /**
     * 从结果 Map 里取列值。
     *
     * <p>MyBatis 的 {@code resultType=map} 用驱动返回的列标签做 key，Oracle 下是大写；但
     * {@code mapUnderscoreToCamelCase} 之类的全局配置在不同基础构件版本上表现过差异，这里同时兜住
     * 大写列名与驼峰名，避免「取不到值→整列写空串」这种静默错账。</p>
     */
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

    /**
     * 把结果 Map 里的时间列转成 {@link Timestamp}，供 Keyset 游标回填。
     *
     * <p>ojdbc8 对 {@code TIMESTAMP} 列的返回类型并不唯一：多数配置下是 {@link Timestamp}，
     * 也可能是驱动自有的 {@code oracle.sql.TIMESTAMP}（它不是 {@link Date} 的子类，直接强转会
     * {@code ClassCastException}）。这里按类型逐级兜底，最后一级用反射调 {@code timestampValue()}，
     * 以免为一个兼容点把已废弃的 {@code oracle.sql} API 硬编进业务代码。</p>
     *
     * <p>返回 null 表示无法识别，调用方会立刻抛异常终止本类文件的抽取 —— **NEVER 静默把游标置回 null**，
     * 那会让 Keyset 从头再翻一遍，形成死循环并重复上送分片。</p>
     */
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
