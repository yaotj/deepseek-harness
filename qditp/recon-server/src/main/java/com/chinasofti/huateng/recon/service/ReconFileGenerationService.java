package com.chinasofti.huateng.recon.service;

import com.chinasofti.huateng.model.recon.ReconFileTypeEnum;
import com.chinasofti.huateng.model.recon.ReconRecord;
import com.chinasofti.huateng.recon.mapper.ReconFileMapper;
import com.chinasofti.huateng.recon.mapper.ReconPartMapper;
import com.chinasofti.huateng.recon.mapper.ReconSourceMapper;
import com.chinasofti.huateng.recon.mapper.ReconStationMapper;
import com.chinasofti.huateng.recon.model.BatchView;
import com.chinasofti.huateng.recon.model.PartReceipt;
import com.chinasofti.huateng.recon.model.ReconBatchStatus;
import com.chinasofti.huateng.recon.model.ReconFileStatus;
import com.chinasofti.huateng.recon.model.ReconFileType;
import com.chinasofti.huateng.recon.model.ReconFileView;
import com.chinasofti.huateng.recon.model.ReconSourceStatus;
import com.chinasofti.huateng.recon.model.SourceProgress;
import com.chinasofti.huateng.recon.storage.ReconLineBackfillProperties;
import com.chinasofti.huateng.recon.storage.ReconStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 把各源上送的分片合并成最终对账文件。
 *
 * <p>两条路径按 {@link ReconFileTypeEnum#isAggregate()} 分流：</p>
 * <ul>
 *   <li>明细（EXP 13 段 / DETAIL 7 段）：1MB 缓冲的**流式字节拼接**，全程不解析内容，
 *       内存占用与文件大小无关；</li>
 *   <li>汇总（PAY 5 键 + 16 度量 / BUS 1 键 + 3 度量）：流式逐行读 + {@link TreeMap} 二次聚合。
 *       键与度量的段数全部取自 {@link ReconFileTypeEnum#getKeyFieldCount()} 与
 *       {@link ReconFileTypeEnum#getMetricFieldCount()}，<b>NEVER 在本类里硬编码下标</b>。
 *       汇总行基数只有几百到几千，放内存安全；<b>明细文件绝不能走这条路径</b>——
 *       几千万行全量进 Map 必然 OOM。</li>
 * </ul>
 *
 * <p>来源清单取自 {@code RECON_BATCH_SOURCE} 中该文件类型 {@code STATUS='COMPLETED'} 的行，
 * 不再读 {@code recon.sources}：各源产出哪些文件类型完全由期望清单决定。</p>
 */
@Service
public class ReconFileGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReconFileGenerationService.class);

    private final ReconBatchService batchService;
    private final ReconPartMapper partMapper;
    private final ReconFileMapper fileMapper;
    private final ReconSourceMapper sourceMapper;
    private final ReconStationMapper stationMapper;
    private final ReconStorageProperties properties;
    private final ReconLineBackfillProperties lineBackfill;

    public ReconFileGenerationService(ReconBatchService batchService,
                                      ReconPartMapper partMapper,
                                      ReconFileMapper fileMapper,
                                      ReconSourceMapper sourceMapper,
                                      ReconStationMapper stationMapper,
                                      ReconStorageProperties properties,
                                      ReconLineBackfillProperties lineBackfill) {
        this.batchService = batchService;
        this.partMapper = partMapper;
        this.fileMapper = fileMapper;
        this.sourceMapper = sourceMapper;
        this.stationMapper = stationMapper;
        this.properties = properties;
        this.lineBackfill = lineBackfill;
    }
    /**
     * 生成某一类型的最终对账文件。
     *
     * @param batchId  批次标识
     * @param fileType 文件类型
     * @return 落库后的文件视图
     */
    public ReconFileView generate(String batchId, ReconFileType fileType) throws IOException {
        validateBatchId(batchId);
        BatchView batch = batchService.get(batchId);
        if (batch == null) throw new IllegalArgumentException("对账批次不存在: " + batchId);
        if (batch.status() == ReconBatchStatus.ALL_SOURCE_COMPLETED || batch.status() == ReconBatchStatus.FAILED) {
            batchService.transition(batchId, ReconBatchStatus.GENERATING);
        } else if (batch.status() != ReconBatchStatus.GENERATING) {
            throw new IllegalStateException("当前批次不可生成文件: " + batch.status());
        }

        ReconFileView existing = fileMapper.select(batchId, fileType.name());
        if (existing != null && ReconFileStatus.GENERATED.name().equals(existing.status())
                && Files.isRegularFile(Path.of(existing.path()))) {
            return existing;
        }

        ReconFileTypeEnum meta = ReconFileTypeEnum.of(fileType.name());
        List<SourceProgress> sources = sourceMapper.selectByBatch(batchId).stream()
                .filter(p -> p.fileType() == fileType && p.status() == ReconSourceStatus.COMPLETED)
                .toList();
        if (sources.isEmpty()) throw new IllegalStateException("该文件类型没有已收齐的来源: " + fileType);

        String fileName = meta.getPrefix() + "." + batch.businessDate();
        Path root = Path.of(properties.getStorageRoot()).toAbsolutePath().normalize();
        Path directory = root.resolve(batchId).resolve("final").normalize();
        if (!directory.startsWith(root)) throw new IllegalArgumentException("非法文件目录");
        Files.createDirectories(directory);
        Path target = directory.resolve(fileName).normalize();
        Path temporary = directory.resolve(fileName + ".generating");

        MergeStats stats = meta.isAggregate()
                ? mergeAggregated(batchId, fileType, meta, root, temporary, sources)
                : mergeDetail(batchId, fileType, root, temporary, sources);
        verify(fileType, meta, sources, stats, temporary);
        moveAtomically(temporary, target);
        fileMapper.upsert(batchId, fileType.name(), fileName, target.toString(),
                stats.bytes(), stats.records(), stats.amount(), stats.sha256(), ReconFileStatus.GENERATED.name());
        return fileMapper.select(batchId, fileType.name());
    }

    /**
     * 明细路径（EXP / DETAIL）：1MB 缓冲流式字节拼接，不解析行内容。
     *
     * <p>行数与金额直接取分片回执上的声明值累加，因此不需要把文件读进内存。</p>
     */
    private MergeStats mergeDetail(String batchId, ReconFileType fileType, Path root, Path target,
                                   List<SourceProgress> sources) throws IOException {
        MessageDigest digest = sha256();
        long bytes = 0;
        long records = 0;
        long amount = 0;
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            for (SourceProgress source : sources) {
                for (PartReceipt part : orderedParts(batchId, source, fileType)) {
                    Path partPath = resolvePart(root, part);
                    try (InputStream in = new BufferedInputStream(Files.newInputStream(partPath))) {
                        byte[] buffer = new byte[1024 * 1024];
                        int read;
                        while ((read = in.read(buffer)) >= 0) {
                            if (read == 0) continue;
                            out.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                            bytes += read;
                        }
                    }
                    records += Objects.requireNonNullElse(part.recordCount(), 0L);
                    amount += Objects.requireNonNullElse(part.amountTotal(), 0L);
                }
            }
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(target);
            throw ex;
        }
        return new MergeStats(bytes, records, amount, HexFormat.of().formatHex(digest.digest()));
    }

    /**
     * 汇总路径（PAY / BUS）：流式逐行读各分片，按前 {@code keyFieldCount} 段键二次聚合后按键升序写出。
     *
     * <p>键与度量的段数完全由 {@link ReconFileTypeEnum} 给出：键是下标
     * {@code [0, keyFieldCount)}，度量是下标 {@code [keyFieldCount, keyFieldCount + metricFieldCount)}
     * 共 {@code metricFieldCount} 个（PAY 16 个、BUS 3 个），逐列累加进 {@code long[metricFieldCount]}。
     * 行字段数不足 {@code keyFieldCount + metricFieldCount} 时直接抛异常——错位一段就是整份错账，
     * <b>NEVER 静默补零放过</b>。</p>
     *
     * <p>汇总行基数只有几百到几千，{@link TreeMap} 常驻内存安全；<b>明细文件绝不能这样做</b>，
     * 几千万行的键全量入 Map 会直接把堆打满。</p>
     */
    private MergeStats mergeAggregated(String batchId, ReconFileType fileType, ReconFileTypeEnum meta,
                                       Path root, Path target, List<SourceProgress> sources) throws IOException {
        int keyFields = meta.getKeyFieldCount();
        int metricFields = meta.getMetricFieldCount();
        int minFields = keyFields + metricFields;
        Map<String, long[]> aggregated = new TreeMap<>();
        try {
            for (SourceProgress source : sources) {
                for (PartReceipt part : orderedParts(batchId, source, fileType)) {
                    Path partPath = resolvePart(root, part);
                    try (BufferedReader reader = Files.newBufferedReader(partPath, StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            String[] fields = ReconRecord.split(line);
                            if (fields.length < minFields) {
                                throw new IllegalStateException("汇总分片字段数不足: " + partPath.getFileName()
                                        + " 期望>=" + minFields + " 实际=" + fields.length);
                            }
                            String key = joinKey(fields, keyFields);
                            long[] metrics = aggregated.computeIfAbsent(key, k -> new long[metricFields]);
                            for (int i = 0; i < metricFields; i++) {
                                metrics[i] += ReconRecord.metric(fields, keyFields + i);
                            }
                        }
                    }
                }
            }
            return writeAggregated(target, backfillLineSegment(fileType, keyFields, aggregated));
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(target);
            throw ex;
        }
    }

    /**
     * 按车站码重算并覆盖「线路」那一段，然后按新键重排。
     *
     * <p>2026-09-16 起线路段由本模块统一补齐，四个源不再各自 {@code LEFT JOIN STATION_INFO}
     * （判据见 {@code ReconStationMapper} 类注释，开关与下标见 {@link ReconLineBackfillProperties}）。</p>
     *
     * <p><b>为什么必须重排而不能就地改字符串</b>：{@link TreeMap} 是按键升序输出的，而键的第 2 段就是线路。
     * 若只把每行的线路段替换掉、沿用原来的迭代顺序，输出行序就变成「按空线路段排」的顺序，
     * 与现行文件（日期 / 线路 / 车站 / 设备 / 支付方式 升序）不一致。因此这里换一个新的
     * {@link TreeMap}、用补好线路的新键重新插入，行序与改造前逐字节一致。</p>
     *
     * <p><b>为什么补完还要按新键合并度量</b>：补齐可能让两个原本不同的键变成同一个键 ——
     * 典型情形是「已改的源留空线路」与「未改的源自带线路」在同一账期同时上送同一个车站的账。
     * 那本来就应该是一组，所以这里对撞上的键**逐列累加**，NEVER 覆盖（覆盖等于丢掉一个源的账）。</p>
     *
     * <p><b>覆盖是无条件的</b>：不看源上送的线路段是空还是有值，一律按车站码重算。理由与
     * 「部署顺序可以自由」的关系见 {@link ReconLineBackfillProperties} 的类注释。</p>
     *
     * <p>维表查不到的车站，线路段留空、该组单独成行，账仍在（与各源原先用外连接的语义一致），
     * 并按不同车站码去重计数后告警一次，便于回头补参数。</p>
     */
    private Map<String, long[]> backfillLineSegment(ReconFileType fileType, int keyFields,
                                                    Map<String, long[]> aggregated) {
        if (!lineBackfill.appliesTo(fileType.name()) || aggregated.isEmpty()) {
            return aggregated;
        }
        int lineIndex = lineBackfill.getLineFieldIndex();
        int stationIndex = lineBackfill.getStationFieldIndex();
        if (lineIndex < 0 || stationIndex < 0 || lineIndex >= keyFields || stationIndex >= keyFields) {
            throw new IllegalStateException("线路补齐的段下标超出键段范围: fileType=" + fileType
                    + " 键段数=" + keyFields + " 线路段=" + lineIndex + " 车站段=" + stationIndex);
        }
        Map<String, String> stationToLine = loadStationLines();
        Map<String, long[]> rekeyed = new TreeMap<>();
        Map<String, Boolean> unknownStations = new HashMap<>();
        for (Map.Entry<String, long[]> entry : aggregated.entrySet()) {
            String[] fields = ReconRecord.split(entry.getKey());
            if (fields.length != keyFields) {
                throw new IllegalStateException("汇总键段数与文件类型不符: fileType=" + fileType
                        + " 期望=" + keyFields + " 实际=" + fields.length);
            }
            String station = fields[stationIndex].trim();
            String line = station.isEmpty() ? "" : stationToLine.get(station);
            if (line == null) {
                line = "";
                unknownStations.put(station, Boolean.TRUE);
            }
            fields[lineIndex] = line;
            long[] metrics = rekeyed.computeIfAbsent(joinKey(fields, keyFields),
                    k -> new long[entry.getValue().length]);
            for (int i = 0; i < entry.getValue().length; i++) {
                metrics[i] += entry.getValue()[i];
            }
        }
        if (!unknownStations.isEmpty()) {
            log.warn("对账文件补线路时有车站在 STATION_INFO 里查不到，这些组的线路段留空、账未丢 "
                    + "fileType={}, 车站数={}, 车站码={}（MUST 回头补参数域的车站维表）",
                    fileType, unknownStations.size(), unknownStations.keySet());
        }
        log.info("对账文件线路段已由 recon-server 统一补齐 fileType={}, 补齐前行数={}, 补齐后行数={}, 维表条目={}",
                fileType, aggregated.size(), rekeyed.size(), stationToLine.size());
        return rekeyed;
    }

    /**
     * 取车站到线路的映射；维表查询失败时抛异常、<b>NEVER 降级成空 Map</b>。
     *
     * <p>降级成空 Map 的后果是「整份 PAY 文件的线路段全空」，而这份文件仍会被判定为生成成功并投递给 ACC ——
     * 那是静默错账。查不出维表就让本次文件生成失败、批次留非终态等下次重入，是这里唯一可接受的行为。</p>
     */
    private Map<String, String> loadStationLines() {
        List<Map<String, Object>> rows = stationMapper.selectStationLineCodes();
        Map<String, String> mapping = new HashMap<>(Math.max(16, rows.size() * 2));
        for (Map<String, Object> row : rows) {
            Object station = row.get("STATION_CODE");
            Object line = row.get("LINE_CODE");
            if (station == null || line == null) continue;
            mapping.put(String.valueOf(station).trim(), String.valueOf(line).trim());
        }
        if (mapping.isEmpty()) {
            throw new IllegalStateException("STATION_INFO 没有任何可用的车站到线路映射，拒绝生成线路段全空的对账文件");
        }
        return mapping;
    }

    /**
     * 按键升序写出 {@code key|m0|m1|...|m(n-1)}，行数即输出行数。
     *
     * <p>{@code amount} 取「所有度量列之和」，口径说明见 {@link #verify}。</p>
     */
    private MergeStats writeAggregated(Path target, Map<String, long[]> aggregated) throws IOException {
        MessageDigest digest = sha256();
        long bytes = 0;
        long records = 0;
        long amount = 0;
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            for (Map.Entry<String, long[]> entry : aggregated.entrySet()) {
                long[] metrics = entry.getValue();
                StringBuilder builder = new StringBuilder(128).append(entry.getKey());
                for (long metric : metrics) {
                    builder.append(ReconRecord.DELIMITER).append(metric);
                    amount += metric;
                }
                builder.append(ReconRecord.LINE_SEPARATOR);
                byte[] payload = builder.toString().getBytes(StandardCharsets.UTF_8);
                out.write(payload);
                digest.update(payload);
                bytes += payload.length;
                records++;
            }
        }
        return new MergeStats(bytes, records, amount, HexFormat.of().formatHex(digest.digest()));
    }

    private String joinKey(String[] fields, int keyFields) {
        StringBuilder builder = new StringBuilder(64);
        for (int i = 0; i < keyFields; i++) {
            if (i > 0) builder.append(ReconRecord.DELIMITER);
            builder.append(fields[i]);
        }
        return builder.toString();
    }

    /**
     * 用各 COMPLETED 来源声明的总账校验实际写出的文件。
     *
     * <p><b>明细文件（EXP / DETAIL）</b>：记录数与金额都必须与声明之和严格相等，不等即删除临时文件并抛异常。</p>
     *
     * <p><b>汇总文件（PAY / BUS）的 {@code amountTotal} 是「该文件所有度量列之和」</b>，
     * 只是用于跨环节比对的校验和（checksum），<b>不是业务金额</b>：PAY 的 16 个度量里有 8 列是笔数、
     * 8 列是金额（下标 6/8/10/12/14/16/18/20，即第 7/9/11/13/15/17/19/21 段），把它们一起相加没有业务含义；
     * BUS 的 3 个度量恰好全是金额，但为口径统一同样按「所有度量之和」计。</p>
     *
     * <p>因此汇总文件 <b>NEVER 与 {@code declaredAmount} 严格比对</b>——源服务声明的是业务金额，
     * 与「所有度量之和」不同量纲，强行相等只会把正常批次全部误判为失败。汇总文件只做健全性检查：
     * 输出行数 &gt; 0、校验和非负。行数同样不可比：多个源可能落在同一组聚合键上，跨源合并后输出行数
     * 必然小于或等于各源声明行数之和，只在大于时告警提示复核源端聚合口径。</p>
     */
    private void verify(ReconFileType fileType, ReconFileTypeEnum meta, List<SourceProgress> sources,
                        MergeStats stats, Path temporary) throws IOException {
        long declaredRecords = sources.stream()
                .mapToLong(source -> Objects.requireNonNullElse(source.declaredRecords(), 0L)).sum();
        long declaredAmount = sources.stream()
                .mapToLong(source -> Objects.requireNonNullElse(source.declaredAmount(), 0L)).sum();
        if (!meta.isAggregate()) {
            if (stats.amount() != declaredAmount) {
                Files.deleteIfExists(temporary);
                throw new IllegalStateException("对账文件金额与来源声明不符: " + fileType
                        + " 实际=" + stats.amount() + " 声明=" + declaredAmount);
            }
            if (stats.records() != declaredRecords) {
                Files.deleteIfExists(temporary);
                throw new IllegalStateException("对账文件记录数与来源声明不符: " + fileType
                        + " 实际=" + stats.records() + " 声明=" + declaredRecords);
            }
            return;
        }
        if (stats.records() <= 0 && declaredRecords > 0) {
            Files.deleteIfExists(temporary);
            throw new IllegalStateException("汇总对账文件没有任何输出行，但来源已声明记录: " + fileType
                    + " 声明记录数=" + declaredRecords);
        }
        if (stats.records() <= 0) {
            log.warn("汇总对账文件为空文件 fileType={}：账期内三个源都没有任何数据，属正常的零交易日。"
                    + "NEVER 把这种情况当失败——甲方要求每个账期都要有文件，空文件也必须投递。", fileType);
        }
        if (stats.amount() < 0) {
            Files.deleteIfExists(temporary);
            throw new IllegalStateException("汇总对账文件度量校验和为负: " + fileType + " 校验和=" + stats.amount());
        }
        if (stats.records() > declaredRecords) {
            log.warn("汇总文件输出行数大于来源声明行数之和，请复核源端聚合口径 fileType={}, 实际={}, 声明={}",
                    fileType, stats.records(), declaredRecords);
        }
        log.info("汇总对账文件校验通过 fileType={}, 行数={}, 度量校验和={}, 源声明业务金额={}（两者量纲不同，不比对）",
                fileType, stats.records(), stats.amount(), declaredAmount);
    }

    /**
     * 取某来源该文件类型的已接收分片，并校验分片号从 0 起连续。
     *
     * <p>{@code partNo} 是装箱的 {@link Integer}（MyBatis 构造器映射要求），先判空兜成 -1 再比，
     * NULL 会直接判定为「序号不连续」并抛异常，而不是抛 NPE。</p>
     */
    private List<PartReceipt> orderedParts(String batchId, SourceProgress source, ReconFileType fileType) {
        List<PartReceipt> parts = partMapper.selectReceivedParts(batchId, source.source(), fileType.name());
        int expectedPartNo = 0;
        for (PartReceipt part : parts) {
            if (Objects.requireNonNullElse(part.partNo(), -1) != expectedPartNo++) {
                throw new IllegalStateException("分片序号不连续: " + source.source());
            }
        }
        return parts;
    }

    private Path resolvePart(Path root, PartReceipt part) throws IOException {
        Path partPath = Path.of(part.path()).toAbsolutePath().normalize();
        if (!partPath.startsWith(root) || !Files.isRegularFile(partPath)) {
            throw new IOException("分片文件不存在或路径非法: " + part.path());
        }
        return partPath;
    }

    private void validateBatchId(String batchId) {
        if (batchId == null || !batchId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("非法批次标识");
    }

    private void moveAtomically(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JDK 不支持 SHA-256", e); }
    }

    private record MergeStats(long bytes, long records, long amount, String sha256) { }
}
