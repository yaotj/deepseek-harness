package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;

import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解约域的**扫表补偿**能力：解约申请批处理、解约通知补发、通道清理补发。
 *
 * <p><b>为什么从 {@code TerminationInternalServiceImpl} 拆出来</b>（2026-09-16，ADR-D95）：那个类原有 7 个入口、
 * 10 个协作者，但它们分属**两个互不相干的驱动**——本类这三个由 web-admin Quartz 定时触发、
 * 输入是「一批待处理记录」、输出是计数；另外四个是外部单笔命令（IF8A-75 / 内部执行 / 拒绝解约 /
 * 失败订单核对），输入是一个请求 DTO。两组只共用 {@code terminationRequestMapper} 一个协作者，
 * 其余各自独立 —— 这正是「一个类里有两簇不相交的依赖」的低内聚信号。
 *
 * <p><b>这是纯搬迁</b>：方法体、日志文案、计数口径、异常处置逐字不变。断言仍挂在
 * {@code TerminationInternalService} 那个对外接口上（它现在委托到本类），因此「全绿」证明的是
 * 行为等价，而不是代码和测试被一起改了。
 *
 * <p><b>本类 NEVER 加 {@code @Transactional}</b>：三个方法都会出网（{@code processOne} 调支付中心、
 * {@code asyncRetryTerminationNotify} 通知 APP、{@code deliver} 调账户域），事务包住网络调用是
 * 2026-08-26 那场生产事故的成因，见 AGENTS.md §5.2。
 */
@Service
public class TerminationCompensationService {

    private static final Logger log = LoggerFactory.getLogger(TerminationCompensationService.class);

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();
    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();

    /**
     * PENDING 滞留多久（分钟）视为「状态回写丢了」，纳入补偿。
     * 必须显著大于外部调度周期（建议 5 分钟）与单次通知超时，否则会把正常在途的通知误判成滞留并重复发送。
     * 与 AppNotifyServiceImpl 的同名常量保持一致。
     */
    private static final int PENDING_STALE_MINUTES = 10;
    /** 单次扫表处理的最大条数，调用方可反复调用直到 scanned 为 0。 */
    private static final int BATCH_SIZE = 200;

    /** referenceTime 入参支持的两种格式：yyyyMMdd（按当天 00:00:00 解析）与 yyyyMMddHHmmss。 */
    private static final DateTimeFormatter REFERENCE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter REFERENCE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 规定天数默认值：解约申请提交满这么多天才确认解约（业务规定 4 天）。
     * 仅在入参 delayDays 为空时生效；入参传 0 表示本次不留延迟。
     */
    @Value("${termination.confirm-delay-days:4}")
    private int confirmDelayDays;

    /**
     * 通知重试上限，超过后该记录不再被补偿扫到，只能人工介入。
     * MUST 与 {@code AppNotifyServiceImpl} 读同一个配置键，否则签约与解约两条补偿链路的重试预算会漂移。
     */
    @Value("${app.notify.max-retry-count:10}")
    private int maxNotifyRetryCount;

    /**
     * 通道清理的重试上限（ADR-D48）。**与通知的上限刻意分开配**：通知失败只是 APP 少收一条消息，
     * 通道没删掉是跨域数据不一致，两者的容忍度不同，NEVER 复用同一个键。
     */
    @Value("${termination.channel-sync.max-retry-count:10}")
    private int maxChannelSyncRetryCount;

    /** 通道清理的唯一投递点，与回调收口的快速路径共用，NEVER 在本类复制其三分支处置。 */
    private final ChannelSyncDeliverer channelSyncDeliverer;

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final AppNotifyService appNotifyService;

    private final TerminationProcessor terminationProcessor;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public TerminationCompensationService(
            ChannelSyncDeliverer channelSyncDeliverer,
            AppTerminationRequestMapper terminationRequestMapper,
            AppNotifyService appNotifyService,
            TerminationProcessor terminationProcessor) {
        this.channelSyncDeliverer = channelSyncDeliverer;
        this.terminationRequestMapper = terminationRequestMapper;
        this.appNotifyService = appNotifyService;
        this.terminationProcessor = terminationProcessor;
    }

    public ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request) {
        ProcessTerminationRespDTO response = new ProcessTerminationRespDTO();
        try {
            LocalDateTime cutoff;
            try {
                cutoff = resolveCutoff(request);
            } catch (DateTimeParseException e) {
                log.warn("解约申请批处理入参 referenceTime 格式非法, request={}", request, e);
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM,
                        "referenceTime 只接受 yyyyMMdd 或 yyyyMMddHHmmss");
                return response;
            }
            // 两类待处理记录：PENDING 待发起解约，SCANNING 待主动查询收口。
            // SCANNING MUST 扫：解约请求报文不含 notifyUrl，回调地址只能由支付中心在商户侧配置，
            // 只等回调会让记录永久卡在 SCANNING、用户实际已解约但 ITP 侧仍显示已签约。
            // cutoff 非空时两类都按同一个申请时间截止点过滤，口径是
            // 「截止点之前仍未成功的都在扫描范围内」；cutoff 为空即历史行为，不按时间过滤。
            List<AppTerminationRequest> records = new ArrayList<>();
            addBatch(records, STATUS_PENDING, cutoff);
            addBatch(records, STATUS_SCANNING, cutoff);
            if (records.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            response.setScanned(records.size());
            for (AppTerminationRequest record : records) {
                try {
                    switch (terminationProcessor.processOne(record)) {
                        case TERMINATED -> response.setTerminated(response.getTerminated() + 1);
                        case CONFIRMED -> response.setConfirmed(response.getConfirmed() + 1);
                        case REJECTED -> response.setRejected(response.getRejected() + 1);
                        case EXPIRED -> response.setExpired(response.getExpired() + 1);
                        case SKIPPED -> response.setSkipped(response.getSkipped() + 1);
                    }
                } catch (Exception e) {
                    // 单条失败不影响本批其余记录。processOne 不带事务（事务内 NEVER 调 RPC），
                    // 状态由它内部的 CAS 语句自行收口：明确失败已 revert 回 PENDING，
                    // 结果未知则刻意留在 SCANNING 等主动查询收口，两者下轮都会被重新扫到。
                    log.error("处理解约申请异常, requestSignSeq={}", record.getRequestSignSeq(), e);
                    response.setSkipped(response.getSkipped() + 1);
                }
            }
            log.info("解约申请批处理完成, cutoff={}, scanned={}, terminated={}, confirmed={}, rejected={}, expired={}, skipped={}",
                    cutoff, response.getScanned(), response.getTerminated(), response.getConfirmed(),
                    response.getRejected(), response.getExpired(), response.getSkipped());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("解约申请批处理异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 算出本次的申请时间截止点：{@code referenceTime - delayDays}。
     *
     * <p>返回 null 表示不按申请时间过滤（入参为 null 或两个字段都为空），与历史行为一致。
     * delayDays 为空时取配置 {@code termination.confirm-delay-days}；为负数按 0 处理
     * （往后推截止点等于放行未来的申请，没有业务含义）。</p>
     *
     * @throws DateTimeParseException referenceTime 既不是 yyyyMMdd 也不是 yyyyMMddHHmmss
     */
    private LocalDateTime resolveCutoff(ProcessTerminationReqDTO request) {
        if (request == null
                || (!StringUtils.hasText(request.getReferenceTime()) && request.getDelayDays() == null)) {
            return null;
        }
        LocalDateTime reference = parseReferenceTime(request.getReferenceTime());
        int delayDays = request.getDelayDays() != null ? request.getDelayDays() : confirmDelayDays;
        if (delayDays < 0) {
            log.warn("delayDays 为负数，按 0 处理, delayDays={}", delayDays);
            delayDays = 0;
        }
        return reference.minusDays(delayDays);
    }

    /** referenceTime 为空取当前时间；yyyyMMdd 按当天 00:00:00 解析。 */
    private LocalDateTime parseReferenceTime(String referenceTime) {
        if (!StringUtils.hasText(referenceTime)) {
            return LocalDateTime.now();
        }
        String trimmed = referenceTime.trim();
        if (trimmed.length() == 8) {
            return LocalDate.parse(trimmed, REFERENCE_DATE_FORMATTER).atStartOfDay();
        }
        return LocalDateTime.parse(trimmed, REFERENCE_DATE_TIME_FORMATTER);
    }

    /** 按状态取一批解约申请追加到待处理列表，cutoff 非空时只取该时刻及之前提交的申请。 */
    private void addBatch(List<AppTerminationRequest> target, String status, LocalDateTime cutoff) {
        List<AppTerminationRequest> batch = cutoff == null
                ? terminationRequestMapper.selectByStatusLimit(status, BATCH_SIZE)
                : terminationRequestMapper.selectByStatusBefore(status, cutoff, BATCH_SIZE);
        if (batch != null && !batch.isEmpty()) {
            target.addAll(batch);
        }
    }

    public CompensateNotifyRespDTO compensateTerminationNotify() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            List<AppTerminationRequest> records = terminationRequestMapper
                    .selectCompensableNotify(maxNotifyRetryCount, PENDING_STALE_MINUTES, BATCH_SIZE);
            if (records == null || records.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            response.setScanned(records.size());
            for (AppTerminationRequest record : records) {
                try {
                    // 提交重发前先落库「这一次尝试」：递增 NOTIFY_RETRY_COUNT 并把状态置为 FAILED。
                    // MUST 在提交前做，且 MUST 对 PENDING 与 FAILED 一视同仁，理由与
                    // AppNotifyServiceImpl.compensateSignNotify 相同（计数先落库才有上限保证）。
                    // 与之配套：通知结果回写 NEVER 再递增计数。口径是「每轮补偿 +1」。
                    terminationRequestMapper.increaseNotifyRetryCount(record.getRequestSignSeq());
                    appNotifyService.asyncRetryTerminationNotify(record);
                    response.setSubmitted(response.getSubmitted() + 1);
                } catch (Exception e) {
                    // 单条提交失败不影响本批其余记录：该条状态未变，下次扫表重试。
                    log.error("提交解约通知重发异常, requestSignSeq={}", record.getRequestSignSeq(), e);
                    response.setSkipped(response.getSkipped() + 1);
                }
            }
            log.info("解约通知补偿完成, scanned={}, submitted={}, skipped={}",
                    response.getScanned(), response.getSubmitted(), response.getSkipped());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("解约通知补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    public CompensateNotifyRespDTO compensateChannelSync() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            List<AppTerminationRequest> pending = terminationRequestMapper
                    .selectCompensableChannelSync(maxChannelSyncRetryCount, PENDING_STALE_MINUTES, BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            // 扫描循环走 OutboxScan（ADR-D46），NEVER 手写 for + try/catch：那三条不变量
            // （单条失败不中断整批 / 每行只计一次 / 兜住两侧异常）已经在骨架里固化并有测试。
            OutboxScan.Result scan = OutboxScan.run(pending,
                    channelSyncDeliverer::deliver,
                    row -> log.warn("通道清理本轮未成功, requestSignSeq={}", row.getRequestSignSeq()),
                    (row, e) -> log.error("单条通道清理异常，NEVER 因此中断整批, requestSignSeq={}",
                            row.getRequestSignSeq(), e));
            response.setScanned(scan.scanned());
            response.setSubmitted(scan.success());
            response.setSkipped(scan.failed());
            log.info("通道清理补偿完成, scanned={}, success={}, failed={}",
                    scan.scanned(), scan.success(), scan.failed());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("通道清理补偿异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }
}
