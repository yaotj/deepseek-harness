package com.chinasofti.huateng.cardpool.service.impl;

import com.chinasofti.huateng.cardpool.config.CardPoolProperties;
import com.chinasofti.huateng.cardpool.entity.LogicCardPoolBatch;
import com.chinasofti.huateng.cardpool.entity.LogicCardPoolCard;
import com.chinasofti.huateng.cardpool.mapper.LogicCardPoolMapper;
import com.chinasofti.huateng.cardpool.service.CardPoolService;
import com.chinasofti.huateng.model.accsecure.RequestQrLogicNumListReqDTO;
import com.chinasofti.huateng.model.accsecure.RequestQrLogicNumListRespDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationReqDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolReservationRespDTO;
import com.chinasofti.huateng.model.cardpool.CardPoolTicketType;
import com.chinasofti.huateng.cardpool.client.AccLogicNumClient;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PreDestroy;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 逻辑卡号池服务实现。 */
@Service
public class CardPoolServiceImpl implements CardPoolService {

    private static final Logger log = LoggerFactory.getLogger(CardPoolServiceImpl.class);

    /** 可作为「已有预占」复用返回的状态白名单。 */
    private static final Set<String> REUSABLE_STATUSES = Set.of("RESERVED", "ASSIGNED");

    /** 可由维护动作继续推进的批次状态白名单。 */
    private static final Set<String> RESUMABLE_STATUSES = Set.of("CREATED", "DOWNLOADING", "IMPORTING");

    /** ACC 成功返回码，两种形态都在生产上出现过。 */
    private static final Set<String> ACC_SUCCESS_CODES = Set.of("0000", "200");

    private static final int SHA256_BUFFER_SIZE = 8192;
    private static final int ERROR_MSG_MAX_LENGTH = 1900;

    /**
     * `ERROR_MSG` 的字节上限。列声明是 `VARCHAR2(2000 CHAR)`，但 Oracle 在
     * `MAX_STRING_SIZE=STANDARD` 下物理上限是 4000 字节，这里留足余量。
     */
    private static final int ERROR_MSG_MAX_BYTES = 1900;

    /** 按字节裁剪时每轮回退的字符数，取小步长以免把多字节字符切得过多。 */
    private static final int TRUNCATE_STEP = 16;

    private static final int CARD_NO_MAX_LENGTH = 64;
    private static final int RESERVE_MAX_ATTEMPTS = 5;

    /** 单次预占从库里取回的候选卡号条数，用于分散并发请求、避免全部撞在同一行上。 */
    private static final int RESERVE_CANDIDATE_SIZE = 20;

    /** 容器关闭时等待在跑导入收尾的秒数。 */
    private static final int EXECUTOR_SHUTDOWN_SECONDS = 30;

    /** 日志全量采集标记在 MDC 中的键名。 */
    private static final String VLOGS_CAPTURE_KEY = "x-vlogs-capture";

    private final LogicCardPoolMapper mapper;
    private final AccLogicNumClient accLogicNumClient;
    private final CardPoolProperties properties;

    /** 链路追踪器，用于把提交线程的 trace 带进维护线程。 */
    private final ObjectProvider<Tracer> tracerProvider;

    /** 承载 ACC 申请、FTP 下载与批量入库的**平台线程**执行器（单线程）。 */
    private final ExecutorService maintenanceExecutor =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("card-pool-maintenance").factory());

    /** 维护任务在跑标记，避免重复提交把任务堆在单线程队列里。 */
    private final AtomicBoolean maintenanceRunning = new AtomicBoolean(false);

    /**
     * 构造卡号池服务。
     *
     * @param mapper            数据访问
     * @param accLogicNumClient ACC 逻辑卡号接口（IF7B-01）适配器
     * @param properties        卡号池配置
     * @param tracerProvider    链路追踪器提供者，tracing 关闭时为空
     */
    public CardPoolServiceImpl(LogicCardPoolMapper mapper,
                               AccLogicNumClient accLogicNumClient,
                               CardPoolProperties properties,
                               ObjectProvider<Tracer> tracerProvider) {
        this.mapper = mapper;
        this.accLogicNumClient = accLogicNumClient;
        this.properties = properties;
        this.tracerProvider = tracerProvider;
    }

    /**
     * 把维护任务包成「挂在调用方 trace 上的子 span」再交给维护线程执行。
     *
     * @param spanName span 名，出现在 trace 拓扑里
     * @param task     真正的维护逻辑
     * @return 已包好 trace 上下文的任务
     */
    private Runnable withTraceContext(String spanName, Runnable task) {
        String capture = MDC.get(VLOGS_CAPTURE_KEY);
        Tracer tracer = tracerProvider.getIfAvailable();
        Span parent = tracer == null ? null : tracer.currentSpan();
        return () -> {
            if (capture != null) {
                MDC.put(VLOGS_CAPTURE_KEY, capture);
            }
            try {
                if (parent == null) {
                    task.run();
                    return;
                }
                Span span = tracer.nextSpan(parent).name(spanName).start();
                try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                    task.run();
                } finally {
                    span.end();
                }
            } finally {
                if (capture != null) {
                    MDC.remove(VLOGS_CAPTURE_KEY);
                }
            }
        };
    }

    /** 容器关闭时停掉维护执行器，给在跑的导入留出收尾时间。 */
    @PreDestroy
    public void shutdown() {
        maintenanceExecutor.shutdown();
        try {
            if (!maintenanceExecutor.awaitTermination(EXECUTOR_SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            maintenanceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CardPoolReservationRespDTO reserve(CardPoolReservationReqDTO request) {
        String cardType = request == null ? null : CardPoolTicketType.normalize(request.getCardType());
        if (cardType == null) {
            throw new IllegalArgumentException("票种码非法或为空: "
                    + (request == null ? null : request.getCardType()));
        }
        if (!CardPoolTicketType.POOL_ENABLED_TYPES.contains(cardType)) {
            throw new IllegalArgumentException("该票种不走逻辑卡号池发号: " + cardType);
        }
        if (!hasBusinessKey(request)) {
            throw new IllegalArgumentException("businessType 与 businessId 不能为空");
        }
        String businessType = request.getBusinessType().trim();
        String businessId = request.getBusinessId().trim();
        LogicCardPoolCard existing = mapper.selectByBusiness(businessType, businessId);
        if (existing != null) {
            return reusableReservation(existing, cardType);
        }
        for (int attempt = 0; attempt < RESERVE_MAX_ATTEMPTS; attempt++) {
            List<LogicCardPoolCard> candidates =
                    mapper.selectAvailableCandidates(cardType, RESERVE_CANDIDATE_SIZE);
            if (candidates == null || candidates.isEmpty()) {
                log.warn("逻辑卡号池已空, cardType={}, businessType={}, businessId={}", cardType, businessType, businessId);
                return null;
            }
            LogicCardPoolCard card = pickCandidate(candidates);
            String reservationId = UUID.randomUUID().toString();
            LocalDateTime expireTime = LocalDateTime.now().plusMinutes(properties.getReservationMinutes());
            try {
                if (mapper.reserve(card.getId(), reservationId, businessType, businessId,
                        trim(request.getOwnerId()), expireTime) == 1) {
                    card.setReservationId(reservationId);
                    card.setExpireTime(expireTime);
                    card.setStatus("RESERVED");
                    return toReservation(card);
                }
            } catch (RuntimeException ex) {
                if (!isIntegrityViolation(ex)) {
                    throw ex;
                }
                LogicCardPoolCard concurrent = mapper.selectByBusiness(businessType, businessId);
                if (concurrent != null) {
                    return reusableReservation(concurrent, cardType);
                }
                throw ex;
            }
        }
        log.warn("逻辑卡号预占重试耗尽, cardType={}, businessType={}, businessId={}", cardType, businessType, businessId);
        return null;
    }

    /**
     * 异常链上是否有唯一键 / 完整性冲突。
     *
     * @param ex 捕获到的异常
     * @return 链上出现过完整性冲突即 true
     */
    private static boolean isIntegrityViolation(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof DataIntegrityViolationException
                    || cursor instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (cursor.getCause() == cursor) {
                return false;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * 从候选卡号中随机挑一条。
     *
     * @param candidates 候选卡号，调用方保证非空
     * @return 本次尝试的目标卡号
     */
    private LogicCardPoolCard pickCandidate(List<LogicCardPoolCard> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /** {@inheritDoc} */
    @Override
    public boolean confirm(String reservationId, String businessId) {
        if (!StringUtils.hasText(reservationId) || !StringUtils.hasText(businessId)) {
            return false;
        }
        if (mapper.confirm(reservationId, businessId) > 0) {
            return true;
        }
        LogicCardPoolCard card = mapper.selectByReservation(reservationId);
        return card != null && "ASSIGNED".equals(card.getStatus()) && businessId.equals(card.getBusinessId());
    }

    /** {@inheritDoc} NEVER 在失败分支调用：预占为并发请求共享，超时回收交 sys_job 107（ADR-D52）。 */
    @Override
    public boolean release(String reservationId, String businessId) {
        if (!StringUtils.hasText(reservationId) || !StringUtils.hasText(businessId)) {
            return false;
        }
        if (mapper.release(reservationId, businessId) > 0) {
            return true;
        }
        LogicCardPoolCard card = mapper.selectByReservation(reservationId);
        return card != null && "AVAILABLE".equals(card.getStatus());
    }

    /** {@inheritDoc} */
    @Override
    public LogicCardPoolBatch requestBatch(String requestedType, String source, String operator) {
        String cardType = CardPoolTicketType.normalize(requestedType);
        if (cardType == null || !CardPoolTicketType.POOL_ENABLED_TYPES.contains(cardType)) {
            throw new IllegalArgumentException("当前票种未启用卡池: " + requestedType);
        }
        String normalizedSource = normalizeSource(source);
        String lockOwner = UUID.randomUUID().toString();
        if (!acquireLock(cardType, lockOwner)) {
            throw new IllegalStateException("该票种正在处理中，请稍后重试");
        }
        try {
            return createBatch(cardType, normalizedSource, operator);
        } finally {
            releaseLock(cardType, lockOwner);
        }
    }

    /** {@inheritDoc} */
    @Override
    public LogicCardPoolBatch retry(Long batchNo) {
        LogicCardPoolBatch batch = batchNo == null ? null : mapper.selectBatch(batchNo);
        if (batch == null) {
            throw new IllegalArgumentException("批次不存在");
        }
        if (!"FAILED".equals(batch.getStatus()) || !StringUtils.hasText(batch.getFileName())) {
            throw new IllegalStateException("仅支持重试已有文件名的失败批次");
        }
        String lockOwner = UUID.randomUUID().toString();
        if (!acquireLock(batch.getCardType(), lockOwner)) {
            throw new IllegalStateException("该票种正在处理中，请稍后重试");
        }
        boolean submitted = false;
        try {
            if (mapper.markBatchRetrying(batchNo) != 1) {
                throw new IllegalStateException("批次状态已变化，无法重试");
            }
            LogicCardPoolBatch retrying = mapper.selectBatch(batchNo);
            maintenanceExecutor.execute(withTraceContext("card-pool-retry", () -> runRetryAsync(retrying, lockOwner)));
            submitted = true;
            return retrying;
        } finally {
            if (!submitted) {
                releaseLock(batch.getCardType(), lockOwner);
            }
        }
    }

    /**
     * 在维护线程上续做重试批次；无论成败都要释放票种锁并留下失败原因。
     *
     * @param batch     已推进到 DOWNLOADING 的批次
     * @param lockOwner 调用方已持有的锁标识
     */
    private void runRetryAsync(LogicCardPoolBatch batch, String lockOwner) {
        try {
            importFromFtp(batch, lockOwner);
        } catch (RuntimeException ex) {
            fail(batch, ex);
        } finally {
            releaseLock(batch.getCardType(), lockOwner);
        }
    }

    /** {@inheritDoc} */
    @Override
    public PageInfo<LogicCardPoolBatch> batches(Long batchNo,
                                                String cardType,
                                                String accTicketType,
                                                String source,
                                                String status,
                                                LocalDateTime createTimeBegin,
                                                LocalDateTime createTimeEnd,
                                                Integer pageNum,
                                                Integer pageSize) {
        return PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> mapper.selectBatches(batchNo, CardPoolTicketType.normalize(cardType),
                        trim(accTicketType), trim(source), trim(status), createTimeBegin, createTimeEnd));
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    /** {@inheritDoc} */
    @Override
    public List<Map<String, Object>> summary() {
        List<Map<String, Object>> result = new ArrayList<>(CardPoolTicketType.ORDERED_TYPES.size());
        for (String type : CardPoolTicketType.ORDERED_TYPES) {
            result.add(summaryOf(type));
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> runMaintenance() {
        Map<String, Object> accepted = new LinkedHashMap<>();
        if (!maintenanceRunning.compareAndSet(false, true)) {
            accepted.put("accepted", false);
            accepted.put("message", "上一轮卡池维护尚未结束，本次请求已忽略");
            return accepted;
        }
        boolean submitted = false;
        try {
            maintenanceExecutor.execute(withTraceContext("card-pool-maintenance", this::runMaintenanceAsync));
            submitted = true;
        } finally {
            if (!submitted) {
                maintenanceRunning.set(false);
            }
        }
        accepted.put("accepted", true);
        accepted.put("message", "卡池维护已受理，执行结果见服务端日志与 /card-pools/summary");
        return accepted;
    }

    /** 在维护线程上执行一轮维护，并在结束后释放并发标记。 */
    private void runMaintenanceAsync() {
        try {
            Map<String, Object> stats = executeMaintenance();
            log.info("卡池维护完成, stats={}", stats);
        } catch (RuntimeException ex) {
            log.error("卡池维护异常终止", ex);
        } finally {
            maintenanceRunning.set(false);
        }
    }

    /**
     * 执行一轮卡池维护：回收超时预占、按票种补货并推进待处理批次。
     *
     * @return 本轮统计
     */
    private Map<String, Object> executeMaintenance() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("releasedExpired", releaseExpiredQuietly());
        int created = 0;
        int executed = 0;
        int failed = 0;
        for (String cardType : CardPoolTicketType.ORDERED_TYPES) {
            if (!CardPoolTicketType.POOL_ENABLED_TYPES.contains(cardType)) {
                continue;
            }
            String lockOwner = UUID.randomUUID().toString();
            if (!acquireLock(cardType, lockOwner)) {
                log.info("票种卡池正被其它执行体处理，本轮跳过, cardType={}", cardType);
                continue;
            }
            try {
                created += replenish(cardType);
                for (LogicCardPoolBatch batch : mapper.selectPendingBatches(cardType)) {
                    executed++;
                    if (!advanceBatch(batch, lockOwner)) {
                        failed++;
                    }
                }
            } catch (RuntimeException ex) {
                failed++;
                log.error("卡池维护失败, cardType={}", cardType, ex);
            } finally {
                releaseLock(cardType, lockOwner);
            }
        }
        stats.put("createdBatches", created);
        stats.put("executedBatches", executed);
        stats.put("failedBatches", failed);
        return stats;
    }

    /**
     * 组装单个票种的水位概览。
     *
     * @param type 票种
     * @return 概览
     */
    private Map<String, Object> summaryOf(String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        boolean poolEnabled = CardPoolTicketType.POOL_ENABLED_TYPES.contains(type);
        long availableCount = poolEnabled ? mapper.countByTypeAndStatus(type, "AVAILABLE") : 0L;
        item.put("cardType", type);
        item.put("accTicketType", CardPoolTicketType.toAccTicketType(type));
        item.put("poolEnabled", poolEnabled);
        item.put("issueMode", issueModeOf(type, poolEnabled));
        item.put("availableCount", availableCount);
        item.put("reservedCount", poolEnabled ? mapper.countByTypeAndStatus(type, "RESERVED") : 0L);
        item.put("assignedCount", poolEnabled ? mapper.countByTypeAndStatus(type, "ASSIGNED") : 0L);
        item.put("belowThreshold", poolEnabled && availableCount < properties.getThreshold());
        List<LogicCardPoolBatch> latest = mapper.selectBatches(null, type, null, null, null, null, null);
        if (!latest.isEmpty()) {
            LogicCardPoolBatch batch = latest.get(0);
            item.put("latestBatchNo", batch.getBatchNo());
            item.put("latestFileName", batch.getFileName());
            item.put("latestStatus", batch.getStatus());
            item.put("latestFinishTime", batch.getFinishTime());
        }
        return item;
    }

    /**
     * 判断票种的发卡方式。
     *
     * @param type        票种
     * @param poolEnabled 是否启用卡池
     * @return POOL / SECURITY_SERVICE / EXTERNAL_ISSUED
     */
    private String issueModeOf(String type, boolean poolEnabled) {
        if (poolEnabled) {
            return "POOL";
        }
        return CardPoolTicketType.SECURITY_SERVICE_TYPES.contains(type) ? "SECURITY_SERVICE" : "EXTERNAL_ISSUED";
    }

    /**
     * 回收超时预占，失败只记日志不阻断后续维护动作。
     *
     * @return 回收数量，失败返回 -1
     */
    private int releaseExpiredQuietly() {
        try {
            return mapper.releaseExpired();
        } catch (RuntimeException ex) {
            log.error("回收超时逻辑卡号预占失败", ex);
            return -1;
        }
    }

    /**
     * 水位低于阈值且无进行中批次时创建一个补货批次。
     *
     * @param cardType 票种
     * @return 创建的批次数，0 或 1
     */
    private int replenish(String cardType) {
        long availableCount = mapper.countByTypeAndStatus(cardType, "AVAILABLE");
        if (availableCount >= properties.getThreshold() || mapper.countInProgress(cardType) > 0) {
            return 0;
        }
        createBatch(cardType, "AUTO", "SYSTEM");
        log.info("自动补充逻辑卡号池, cardType={}, availableCount={}, threshold={}",
                cardType, availableCount, properties.getThreshold());
        return 1;
    }

    /**
     * 在已持有票种锁的前提下落一条 CREATED 批次。
     *
     * @param cardType 票种
     * @param source   来源
     * @param operator 操作人
     * @return 已落库批次
     */
    private LogicCardPoolBatch createBatch(String cardType, String source, String operator) {
        if (mapper.countInProgress(cardType) > 0) {
            throw new IllegalStateException("该票种已有进行中的卡池批次");
        }
        Long batchNo = mapper.nextBatchNo();
        if (batchNo >= properties.getSequenceAlertThreshold()) {
            log.error("LOGIC_CARD_POOL_BATCH_SEQ 接近上限: batchNo={}, threshold={}; 请立即完成序列扩容或新序列切换",
                    batchNo, properties.getSequenceAlertThreshold());
        }
        LogicCardPoolBatch batch = new LogicCardPoolBatch();
        batch.setBatchNo(batchNo);
        batch.setRequestSeq(String.valueOf(batchNo));
        batch.setCardType(cardType);
        batch.setAccTicketType(CardPoolTicketType.toAccTicketType(cardType));
        batch.setRequestNum(properties.getRequestNum());
        batch.setSource(source);
        batch.setStatus("CREATED");
        batch.setOperator(trim(operator));
        batch.setTotalCount(0);
        batch.setValidCount(0);
        batch.setDuplicateCount(0);
        batch.setInvalidCount(0);
        batch.setRetryCount(0);
        mapper.insertBatch(batch);
        return mapper.selectBatch(batchNo);
    }

    /**
     * 推进一个待处理批次。
     *
     * @param batch     批次
     * @param lockOwner 当前持有的锁标识
     * @return 推进到 SUCCESS 返回 true
     */
    private boolean advanceBatch(LogicCardPoolBatch batch, String lockOwner) {
        if ("REQUESTING".equals(batch.getStatus())) {
            fail(batch, "ACC 申请结果未知（进程在申请过程中中断），需人工核对 ACC 侧是否已生成该批次后重建");
            return false;
        }
        if (!RESUMABLE_STATUSES.contains(batch.getStatus())) {
            log.warn("批次状态不允许推进, batchNo={}, status={}", batch.getBatchNo(), batch.getStatus());
            return false;
        }
        try {
            if ("CREATED".equals(batch.getStatus())) {
                requestFromAcc(batch);
            }
            importFromFtp(batch, lockOwner);
        } catch (RuntimeException ex) {
            fail(batch, ex);
            return false;
        }
        return "SUCCESS".equals(batch.getStatus());
    }

    /**
     * 向 ACC 申请逻辑卡号文件，成功后把文件名落库并推进到 DOWNLOADING。
     *
     * @param batch 批次
     */
    private void requestFromAcc(LogicCardPoolBatch batch) {
        batch.setStatus("REQUESTING");
        mapper.updateBatch(batch);
        RequestQrLogicNumListReqDTO request = new RequestQrLogicNumListReqDTO();
        request.setRequestNum(String.valueOf(batch.getRequestNum()));
        request.setRequestSeq(batch.getRequestSeq());
        request.setTicketType(batch.getAccTicketType());
        RequestQrLogicNumListRespDTO response = accLogicNumClient.requestQrLogicNumList(request);
        String retCode = response == null ? null : response.getRetCode();
        String fileName = response == null ? null : response.getFileName();
        if (retCode == null || !ACC_SUCCESS_CODES.contains(retCode) || !isSafeFileName(fileName)) {
            throw new IllegalStateException("ACC申请失败或未返回合法文件名, retCode=" + retCode + ", fileName=" + fileName);
        }
        batch.setFileName(fileName);
        batch.setFtpPath(properties.getFtp().getBaseDir());
        batch.setStatus("DOWNLOADING");
        mapper.updateBatch(batch);
    }

    /**
     * 下载 ACC 文件并导入卡号；临时文件在任何异常路径下都会被清理。
     *
     * @param batch     批次
     * @param lockOwner 当前持有的锁标识，用于导入过程中续期
     */
    private void importFromFtp(LogicCardPoolBatch batch, String lockOwner) {
        Path tempFile = null;
        try {
            renewLockOrFail(batch.getCardType(), lockOwner);
            tempFile = download(batch.getFileName());
            renewLockOrFail(batch.getCardType(), lockOwner);
            batch.setStatus("IMPORTING");
            batch.setFileSize(Files.size(tempFile));
            batch.setFileSha256(sha256(tempFile));
            mapper.updateBatch(batch);
            ImportStats stats = importText(batch, tempFile, lockOwner);
            batch.setTotalCount(stats.total);
            batch.setValidCount(stats.valid);
            batch.setDuplicateCount(stats.duplicates);
            batch.setInvalidCount(stats.invalid);
            if (stats.valid == 0) {
                batch.setStatus("FAILED");
                batch.setErrorMsg("解析出 " + stats.total + " 行但零条合法卡号（重复 " + stats.duplicates
                        + "，非法 " + stats.invalid + "），请核对 ACC 文件格式");
                log.error("逻辑卡号文件零条入库, batchNo={}, fileName={}, total={}, duplicates={}, invalid={}",
                        batch.getBatchNo(), batch.getFileName(), stats.total, stats.duplicates, stats.invalid);
            } else {
                batch.setStatus("SUCCESS");
                batch.setErrorMsg(null);
            }
            batch.setFinishTime(LocalDateTime.now());
            mapper.updateBatch(batch);
        } catch (IOException ex) {
            throw new IllegalStateException("逻辑卡号文件下载或导入失败: " + ex.getMessage(), ex);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    /**
     * 逐行解析并分片入库。
     *
     * @param batch     批次
     * @param file      本地临时文件
     * @param lockOwner 当前持有的锁标识
     * @return 导入统计
     * @throws IOException 读文件失败
     */
    private ImportStats importText(LogicCardPoolBatch batch, Path file, String lockOwner) throws IOException {
        ImportStats stats = new ImportStats();
        int chunkSize = properties.getImportChunkSize();
        List<LogicCardPoolCard> cards = new ArrayList<>(chunkSize);
        Set<String> seenCards = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                stats.total++;
                String cardNo = parseCardNo(line, batch.getAccTicketType());
                if (cardNo == null) {
                    stats.invalid++;
                    log.warn("逻辑卡号行不合法，跳过: batchNo={}, accTicketType={}, line={}",
                            batch.getBatchNo(), batch.getAccTicketType(), line.trim());
                    continue;
                }
                if (!seenCards.add(cardNo)) {
                    stats.duplicates++;
                    continue;
                }
                LogicCardPoolCard card = new LogicCardPoolCard();
                card.setCardNo(cardNo);
                card.setBatchNo(batch.getBatchNo());
                card.setCardType(batch.getCardType());
                cards.add(card);
                if (cards.size() == chunkSize) {
                    flushCards(cards, stats);
                    renewLockOrFail(batch.getCardType(), lockOwner);
                }
            }
        }
        flushCards(cards, stats);
        return stats;
    }

    /**
     * 续期票种级批次锁，续不上即中止本次导入。
     *
     * @param cardType  票种
     * @param lockOwner 当前持有的锁标识
     */
    private void renewLockOrFail(String cardType, String lockOwner) {
        if (mapper.renewBatchLock(cardType, lockOwner) != 1) {
            throw new IllegalStateException("票种卡池锁已失效（可能已超时被其它执行体接管），中止本次导入, cardType=" + cardType);
        }
    }

    /**
     * 分片入库；批量失败后逐条重试，只有确定是唯一键冲突才按重复处理，其余异常一律上抛。
     *
     * @param cards 待入库分片
     * @param stats 导入统计
     */
    private void flushCards(List<LogicCardPoolCard> cards, ImportStats stats) {
        if (cards.isEmpty()) {
            return;
        }
        try {
            mapper.insertCards(cards);
            stats.valid += cards.size();
        } catch (RuntimeException batchFailure) {
            if (!isIntegrityViolation(batchFailure)) {
                throw batchFailure;
            }
            for (LogicCardPoolCard card : cards) {
                insertSingleCard(card, stats);
            }
        }
        cards.clear();
    }

    /**
     * 单条入库并识别冲突原因。
     *
     * @param card  卡号
     * @param stats 导入统计
     */
    private void insertSingleCard(LogicCardPoolCard card, ImportStats stats) {
        try {
            mapper.insertCards(List.of(card));
            stats.valid++;
        } catch (RuntimeException conflict) {
            if (!isIntegrityViolation(conflict)) {
                throw conflict;
            }
            LogicCardPoolCard existing = mapper.selectByCardNo(card.getCardNo());
            if (existing == null) {
                throw new IllegalStateException("逻辑卡号入库失败且库中无同号记录, cardNo=" + card.getCardNo(), conflict);
            }
            if (sameBatchAndType(existing, card)) {
                stats.valid++;
                return;
            }
            stats.duplicates++;
            if (!card.getCardType().equals(existing.getCardType())) {
                log.error("逻辑卡号跨票种冲突, cardNo={}, importingType={}, existingType={}, existingBatch={}",
                        card.getCardNo(), card.getCardType(), existing.getCardType(), existing.getBatchNo());
            }
        }
    }

    /**
     * 从 FTP 下载指定文件到本地临时文件。
     *
     * @param fileName ACC 返回的文件名
     * @return 本地临时文件路径
     * @throws IOException FTP 配置非法、文件不存在、超限或传输失败
     */
    private Path download(String fileName) throws IOException {
        CardPoolProperties.Ftp ftpConfig = properties.getFtp();
        if (!isSafeFileName(fileName) || !StringUtils.hasText(ftpConfig.getHost())) {
            throw new IOException("FTP配置或文件名不合法");
        }
        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(ftpConfig.getConnectTimeoutMillis());
        ftp.setDefaultTimeout(ftpConfig.getConnectTimeoutMillis());
        ftp.setControlEncoding(StandardCharsets.UTF_8.name());
        Path target = Files.createTempFile("logic-card-pool-", ".txt");
        try {
            connectAndLogin(ftp, ftpConfig);
            FTPFile[] files = ftp.listFiles(fileName);
            if (files.length != 1 || files[0].getSize() <= 0 || files[0].getSize() > ftpConfig.getMaxFileSize()) {
                throw new IOException("FTP文件不存在、为空或超出限制: " + fileName);
            }
            try (OutputStream output = new SizeLimitedOutputStream(Files.newOutputStream(target),
                    ftpConfig.getMaxFileSize(), fileName)) {
                if (!ftp.retrieveFile(fileName, output)) {
                    throw new IOException("FTP下载失败: " + fileName);
                }
            }
            return target;
        } catch (Exception ex) {
            deleteQuietly(target);
            throw ex;
        } finally {
            disconnectQuietly(ftp);
        }
    }

    /**
     * 建立 FTP 连接、登录并切换到目标目录，同时设置数据连接超时。
     *
     * @param ftp        FTP 客户端
     * @param ftpConfig  FTP 配置
     * @throws IOException 连接、登录或切目录失败
     */
    private void connectAndLogin(FTPClient ftp, CardPoolProperties.Ftp ftpConfig) throws IOException {
        ftp.connect(ftpConfig.getHost(), ftpConfig.getPort());
        ftp.setSoTimeout(ftpConfig.getSoTimeoutMillis());
        ftp.setDataTimeout(Duration.ofSeconds(ftpConfig.getDataTimeoutSeconds()));
        if (!ftp.login(ftpConfig.getUsername(), ftpConfig.getPassword())) {
            throw new IOException("FTP登录失败");
        }
        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
        if (!ftp.changeWorkingDirectory(ftpConfig.getBaseDir())) {
            throw new IOException("FTP目录不可用: " + ftpConfig.getBaseDir());
        }
    }

    /**
     * 关闭 FTP 连接，失败只记日志。
     *
     * @param ftp FTP 客户端
     */
    private void disconnectQuietly(FTPClient ftp) {
        if (!ftp.isConnected()) {
            return;
        }
        try {
            ftp.logout();
        } catch (IOException ex) {
            log.warn("FTP登出失败", ex);
        }
        try {
            ftp.disconnect();
        } catch (IOException ex) {
            log.warn("FTP断开连接失败", ex);
        }
    }

    /**
     * 删除临时文件，失败只记日志。
     *
     * @param file 临时文件，可为 null
     */
    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("删除卡池临时文件失败: {}", file, ex);
        }
    }

    /**
     * 抢占票种锁。
     *
     * @param cardType  票种
     * @param lockOwner 锁标识
     * @return 抢到返回 true
     */
    private boolean acquireLock(String cardType, String lockOwner) {
        return mapper.acquireBatchLock(cardType, lockOwner, properties.getLockStaleSeconds()) == 1;
    }

    /**
     * 释放票种锁，失败只记日志，不覆盖业务异常。
     *
     * @param cardType  票种
     * @param lockOwner 锁标识
     */
    private void releaseLock(String cardType, String lockOwner) {
        try {
            mapper.releaseBatchLock(cardType, lockOwner);
        } catch (RuntimeException ex) {
            log.error("释放卡池批次锁失败, cardType={}, lockOwner={}", cardType, lockOwner, ex);
        }
    }

    /**
     * 记录批次失败，异常本身与落库失败都不会互相掩盖。
     *
     * @param batch 批次
     * @param cause 失败异常
     */
    private void fail(LogicCardPoolBatch batch, Exception cause) {
        log.error("卡池批次失败, batchNo={}, cardType={}", batch.getBatchNo(), batch.getCardType(), cause);
        fail(batch, cause.getMessage());
    }

    /**
     * 记录批次失败原因；`updateBatch` 失败时降级为最小 UPDATE，确保批次一定离开中间态。
     *
     * @param batch  批次
     * @param reason 失败原因
     */
    private void fail(LogicCardPoolBatch batch, String reason) {
        batch.setStatus("FAILED");
        batch.setErrorMsg(truncate(reason));
        batch.setFinishTime(LocalDateTime.now());
        try {
            mapper.updateBatch(batch);
            return;
        } catch (RuntimeException ex) {
            log.error("写入卡池批次失败状态时出错，降级为最小化落库, batchNo={}, reason={}",
                    batch.getBatchNo(), reason, ex);
        }
        mapper.markBatchFailed(batch.getBatchNo(), batch.getErrorMsg());
    }

    /**
     * 判断请求是否带齐业务归属键。
     *
     * @param request 预占请求
     * @return 带齐返回 true
     */
    private boolean hasBusinessKey(CardPoolReservationReqDTO request) {
        return request != null
                && StringUtils.hasText(request.getBusinessType())
                && StringUtils.hasText(request.getBusinessId());
    }

    /**
     * 按状态白名单决定已有记录能否作为预占复用返回。
     *
     * @param existing 已有记录
     * @param cardType 本次请求票种
     * @return 可复用的预占，否则返回 null
     */
    private CardPoolReservationRespDTO reusableReservation(LogicCardPoolCard existing, String cardType) {
        if (!cardType.equals(existing.getCardType())) {
            log.warn("业务归属已占用其它票种卡号, businessType={}, businessId={}, existingType={}, requestType={}",
                    existing.getBusinessType(), existing.getBusinessId(), existing.getCardType(), cardType);
            throw new IllegalStateException("该业务归属已占用其它票种卡号: existingType=" + existing.getCardType()
                    + ", requestType=" + cardType);
        }
        if (!REUSABLE_STATUSES.contains(existing.getStatus())) {
            log.error("业务归属的卡号状态不允许复用预占, businessId={}, cardNo={}, status={}",
                    existing.getBusinessId(), existing.getCardNo(), existing.getStatus());
            throw new IllegalStateException("该业务归属的卡号状态不允许复用预占: status=" + existing.getStatus());
        }
        return toReservation(existing);
    }

    /**
     * 转换为预占响应。
     *
     * @param card 卡号记录
     * @return 预占响应
     */
    private CardPoolReservationRespDTO toReservation(LogicCardPoolCard card) {
        CardPoolReservationRespDTO dto = new CardPoolReservationRespDTO();
        dto.setReservationId(card.getReservationId());
        dto.setCardNo(card.getCardNo());
        dto.setCardType(card.getCardType());
        dto.setExpireTime(card.getExpireTime() == null
                ? null
                : card.getExpireTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return dto;
    }

    /**
     * 校验 ACC 返回的文件名，拒绝路径穿越与非 txt。
     *
     * @param value 文件名
     * @return 合法返回 true
     */
    private boolean isSafeFileName(String value) {
        return StringUtils.hasText(value)
                && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,240}\\.txt")
                && !value.contains("..");
    }

    /**
     * 解析 ACC 逻辑卡号文件的一行，取第一列作卡号并校验第二列票种。
     *
     * @param line                 原始行
     * @param expectedAccTicketType 批次的 ACC 票种，为空则不校验第二列
     * @return 合法时返回卡号，否则返回 {@code null}
     */
    static String parseCardNo(String line, String expectedAccTicketType) {
        if (line == null) {
            return null;
        }
        String[] columns = line.trim().split("\\s+");
        String cardNo = columns[0];
        if (!isValidCardNo(cardNo)) {
            return null;
        }
        if (columns.length > 1 && StringUtils.hasText(expectedAccTicketType)
                && !columns[1].equalsIgnoreCase(expectedAccTicketType.trim())) {
            return null;
        }
        return cardNo;
    }

    /**
     * 校验逻辑卡号格式，仅允许字母数字且不超过列长。
     *
     * @param value 卡号
     * @return 合法返回 true
     */
    private static boolean isValidCardNo(String value) {
        return StringUtils.hasText(value)
                && value.length() <= CARD_NO_MAX_LENGTH
                && value.matches("[A-Za-z0-9]+");
    }

    /**
     * 计算文件 SHA-256 摘要。
     *
     * @param file 文件
     * @return 十六进制摘要
     */
    private String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[SHA256_BUFFER_SIZE];
            for (int length; (length = input.read(buffer)) >= 0; ) {
                digest.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("计算逻辑卡号文件摘要失败", ex);
        }
    }

    /**
     * 判断已有记录是否来自同一批次同一票种，用于中断后重跑保留原有计数。
     *
     * @param existing 已有记录
     * @param card     本次待入库记录
     * @return 同批次同票种返回 true
     */
    private boolean sameBatchAndType(LogicCardPoolCard existing, LogicCardPoolCard card) {
        return card.getBatchNo().equals(existing.getBatchNo())
                && card.getCardType().equals(existing.getCardType());
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 把失败原因裁剪到 `ERROR_MSG` 列能容纳的范围。
     *
     * @param value 原始失败原因
     * @return 裁剪后的原因，入参为空时返回占位串
     */
    private String truncate(String value) {
        if (value == null) {
            return "未知错误";
        }
        String limited = value.length() <= ERROR_MSG_MAX_LENGTH
                ? value
                : value.substring(0, ERROR_MSG_MAX_LENGTH);
        while (limited.getBytes(StandardCharsets.UTF_8).length > ERROR_MSG_MAX_BYTES) {
            limited = limited.substring(0, Math.max(0, limited.length() - TRUNCATE_STEP));
        }
        return limited.isEmpty() ? "未知错误" : limited;
    }

    /**
     * 归一化申请来源。
     *
     * @param source 来源
     * @return AUTO 或 MANUAL
     */
    private String normalizeSource(String source) {
        if ("AUTO".equalsIgnoreCase(source)) {
            return "AUTO";
        }
        if ("MANUAL".equalsIgnoreCase(source)) {
            return "MANUAL";
        }
        throw new IllegalArgumentException("不支持的卡池申请来源: " + source);
    }

    /** 单批次导入统计。 */
    private static final class ImportStats {
        private int total;
        private int valid;
        private int duplicates;
        private int invalid;
    }

    /** 带字节上限的输出流。 */
    private static final class SizeLimitedOutputStream extends OutputStream {

        private final OutputStream delegate;
        private final long maxBytes;
        private final String fileName;
        private long written;

        private SizeLimitedOutputStream(OutputStream delegate, long maxBytes, String fileName) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
            this.fileName = fileName;
        }

        @Override
        public void write(int b) throws IOException {
            ensureCapacity(1);
            delegate.write(b);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            ensureCapacity(length);
            delegate.write(buffer, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        /**
         * 累计已写字节并在超限时中止传输。
         *
         * @param increment 本次将写入的字节数
         * @throws IOException 超出配置上限
         */
        private void ensureCapacity(int increment) throws IOException {
            written += increment;
            if (written > maxBytes) {
                throw new IOException("FTP文件实际大小超出限制 " + maxBytes + " 字节: " + fileName);
            }
        }
    }
}
