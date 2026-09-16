package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.entity.F2fRefund;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fRefundMapper;
import com.chinasofti.huateng.facepay.support.F2fOrderNo;
import com.chinasofti.huateng.facepay.support.F2fOrderNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 退款编排。<b>本模块所有退款入口都必须走这里</b>，共 7 个来源（见 {@code CK_F2F_REFUND_SOURCE}）。
 *
 * <h2>四条不可违反的约束</h2>
 * <ol>
 *   <li><b>防重靠唯一函数索引，不靠先查后插。</b>
 *       {@code UK_F2F_REFUND_IDEM (ORIG_ORDER_NO, NVL(TICKET_LOGIC_NUM,'#WHOLE#'), REFUND_SOURCE)}。
 *       并发下「先 SELECT 判断退过没再 INSERT」两个线程都查不到就都插进去，等于重复退款——
 *       这是旧实现的最高优先级缺陷（{@code TvmCommonServiceImpl.doRefund} 无任何幂等键）。</li>
 *   <li><b>对端没答上来 NEVER 置 FAILED。</b>退款请求发出去了、钱可能已经退了，此时置 FAILED
 *       会让补偿任务再退一次。留在 {@code INIT} 由 {@link #reconcileRefund} 查询收口。
 *       旧实现的 {@code doRefund} 反过来——<b>不管对端答什么都 {@code return true}</b>，
 *       调用方据此写「已退款」标记，退款失败也被记成已退。</li>
 *   <li><b>整个类不带 {@code @Transactional}</b>：链路里有支付中心调用（AGENTS.md §5.2）。
 *       顺序固定为「INSERT 退款单 → 事务外调支付中心 → UPDATE 结果」。</li>
 *   <li><b>状态推进走白名单</b>，{@code fromStatuses} 一律显式传。</li>
 * </ol>
 *
 * <h2>状态机</h2>
 * <pre>
 * INIT --受理成功--> PROCESSING --查询确认--> SUCCESS
 *   |                     |
 *   |                     +--查询确认失败--> FAILED
 *   |                     +--次数用尽或超时间窗仍查不到--> MANUAL
 *   +--对端未答--> 留 INIT，扫表按指数退避重查（不置 FAILED）
 * </pre>
 *
 * <h2>收口的退避与放弃</h2>
 * <p>间隔从 {@code backoffBaseSeconds}（默认 300 秒）起翻倍、封顶
 * {@code backoffMaxSeconds}（默认 3600 秒），即 5 / 10 / 20 / 40 分钟后转为每小时一次。
 * 放弃条件有两个，<b>谁先到算谁</b>：查询次数达到 {@code maxQueryTimes}（默认 30），
 * 或 {@code REQUEST_TMS} 距今超过 {@code giveUpAfterHours}（默认 24 小时，
 * 即一个支付中心对账周期）。按上面的退避序列，约 27 次覆盖满 24 小时，
 * 因此正常情况下时间窗先到、次数上限是兜底。</p>
 *
 * <p><b>次数上限 MUST 在 application 层判定，NEVER 写进扫表谓词。</b>
 * 两者的差别是致命的：写进 SQL 的 {@code RETRY_TIMES < N} 一旦用尽，那笔单
 * 直接从扫描结果里消失、状态停在 INIT / PROCESSING、无告警无人工入口
 * （原实现「固定 60 秒 × 20 次」就是这样在 20 分钟内静默丢单的）；
 * 放在这里判定则会显式置 {@link #STATUS_MANUAL} 并打 ERROR。</p>
 */
@Service
public class F2fRefundService {

    /** 出票故障自动退（差额退）。 */
    public static final String SOURCE_TAKE_TICKET_FAIL = "TAKE_TICKET_FAIL";

    /** BOM 单程票原路退。 */
    public static final String SOURCE_BOM_ORIGINAL = "BOM_ORIGINAL";

    /** APP 用户主动退。 */
    public static final String SOURCE_APP_REQUEST = "APP_REQUEST";

    /** 每日批量退未取票交易。 */
    public static final String SOURCE_DAILY_BATCH = "DAILY_BATCH";

    /** 充值写卡失败退。 */
    public static final String SOURCE_TOPUP_FAIL = "TOPUP_FAIL";

    /** 运营端手工退。 */
    public static final String SOURCE_PAGE_MANUAL = "PAGE_MANUAL";

    /** 设备侧 {@code requestRefund} 发起。 */
    public static final String SOURCE_TVM_REQUEST = "TVM_REQUEST";

    static final String STATUS_INIT = "INIT";

    static final String STATUS_PROCESSING = "PROCESSING";

    static final String STATUS_SUCCESS = "SUCCESS";

    static final String STATUS_FAILED = "FAILED";

    /**
     * 超过自动收口时间窗仍拿不到明确结果，转人工介入。
     *
     * <p><b>NEVER 把它当成「退款失败」</b>：钱到底退没退是未知的，
     * 这个状态的含义是「本系统已放弃自动判定，需要人工查支付中心或走对账」。</p>
     */
    static final String STATUS_MANUAL = "MANUAL";

    /** 可推进到终态的前置状态白名单。 */
    private static final List<String> PENDING_STATUSES = List.of(STATUS_INIT, STATUS_PROCESSING);

    private static final Logger log = LoggerFactory.getLogger(F2fRefundService.class);

    private final F2fRefundMapper refundMapper;

    /**
     * 只用于退款收口后重算订单上的退款汇总三列（{@code REFUND_STATUS} / {@code REFUND_AMOUNT}
     * / {@code LAST_REFUND_TMS}）。<b>NEVER 用它改 {@code ORDER_STATUS}</b> ——
     * 退款与支付/履约主状态正交（ADR-D88），参考实现是 {@code PAY_TXN_DETAIL}。
     */
    private final F2fOrderMapper orderMapper;

    private final F2fOrderNoGenerator orderNoGenerator;

    private final PayCenterClient payCenterClient;

    private final PayCenterMessageFactory messageFactory;

    private final long firstQueryDelaySeconds;

    private final long backoffBaseSeconds;

    private final long backoffMaxSeconds;

    private final int maxQueryTimes;

    private final long giveUpAfterHours;

    public F2fRefundService(F2fRefundMapper refundMapper, F2fOrderMapper orderMapper,
                            F2fOrderNoGenerator orderNoGenerator,
                            PayCenterClient payCenterClient, PayCenterMessageFactory messageFactory,
                            @Value("${f2f.refund.firstQueryDelaySeconds:60}") long firstQueryDelaySeconds,
                            @Value("${f2f.refund.backoffBaseSeconds:300}") long backoffBaseSeconds,
                            @Value("${f2f.refund.backoffMaxSeconds:3600}") long backoffMaxSeconds,
                            @Value("${f2f.refund.maxQueryTimes:30}") int maxQueryTimes,
                            @Value("${f2f.refund.giveUpAfterHours:24}") long giveUpAfterHours) {
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.payCenterClient = payCenterClient;
        this.messageFactory = messageFactory;
        this.firstQueryDelaySeconds = firstQueryDelaySeconds;
        this.backoffBaseSeconds = backoffBaseSeconds;
        this.backoffMaxSeconds = backoffMaxSeconds;
        this.maxQueryTimes = maxQueryTimes;
        this.giveUpAfterHours = giveUpAfterHours;
    }

    /**
     * 发起一笔退款。
     *
     * <p><b>返回值 MUST 检查</b>：{@link RefundOutcome#alreadyExisted()} 为 true 表示这笔
     * 「原订单 + 票 + 来源」已经退过，调用方 NEVER 再重复触发下游动作（如再改一次票状态）。</p>
     *
     * @return 退款结果；参数非法时返回 {@link RefundOutcome#rejected(String)}
     */
    public RefundOutcome refund(RefundCommand command) {
        String reject = command.validate();
        if (reject != null) {
            log.warn("退款请求参数非法被拒, command={}, reason={}", command, reject);
            return RefundOutcome.rejected(reject);
        }

        String refundNo = orderNoGenerator.next(F2fOrderNo.BIZ_REFUND);
        F2fRefund refund = command.toEntity(refundNo, LocalDateTime.now());
        refund.setNextQueryTms(refund.getRequestTms() == null
                ? LocalDateTime.now().plusSeconds(firstQueryDelaySeconds)
                : refund.getRequestTms().plusSeconds(firstQueryDelaySeconds));
        try {
            refundMapper.insert(refund);
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            F2fRefund existing = findExisting(command);
            if (existing == null) {
                log.error("退款单撞唯一索引却查不回已有单，需人工核查, origOrderNo={}, source={}",
                        command.origOrderNo(), command.refundSource());
                return RefundOutcome.rejected("退款单状态异常，请人工核查");
            }
            log.info("该笔已退过，幂等返回已有退款单, refundNo={}, status={}",
                    existing.getRefundNo(), existing.getRefundStatus());
            return new RefundOutcome(existing.getRefundNo(), existing.getRefundStatus(), true, null);
        }
        log.info("退款单已落库, refundNo={}, origOrderNo={}, source={}, amount={}",
                refundNo, command.origOrderNo(), command.refundSource(), command.refundAmount());

        return submitToPayCenter(refundNo, command);
    }

    /**
     * 把退款单送到支付中心。<b>必须在事务外</b>。
     *
     * <p>三个分支：受理成功置 {@code PROCESSING} 等收口；对端未答或业务失败<b>留在 INIT</b>
     * 并累加重试次数——NEVER 在这里置 FAILED。</p>
     */
    private RefundOutcome submitToPayCenter(String refundNo, RefundCommand command) {
        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getRefundUrl(),
                messageFactory.buildRefundRequest(refundNo, command.origOrderNo(),
                        command.payCenterOrderNo(), command.refundAmount()));

        if (result.isTransportFailed() || !result.isSuccessCode()) {
            String reason = result.isTransportFailed() ? result.getFailureReason() : result.getMsg();
            refundMapper.increaseRetryTimes(refundNo, truncate(reason), nextQueryTms(0));
            log.error("退款未被支付中心受理，留在 INIT 等扫表收口（NEVER 置 FAILED，钱可能已退）,"
                    + " refundNo={}, transportFailed={}, code={}", refundNo, result.isTransportFailed(),
                    result.getCode());
            return new RefundOutcome(refundNo, STATUS_INIT, false, reason);
        }

        int updated = refundMapper.updateStatus(refundNo, List.of(STATUS_INIT), STATUS_PROCESSING,
                result.string("refundOrderNo"), null);
        log.info("退款已被支付中心受理, refundNo={}, updatedRows={}", refundNo, updated);
        return new RefundOutcome(refundNo, STATUS_PROCESSING, false, null);
    }

    /**
     * 退款收口：向支付中心查这笔退款的最终结果并推进本地状态。供 {@code @Scheduled} 补偿任务调用。
     *
     * <p>{@code INIT} 的单也要查——「对端未答」不等于「没受理」，可能请求已到达。
     * <b>本方法只发 refundQuery、NEVER 重发退款</b>：重发是资金动作，必须另有严格限次的入口。</p>
     *
     * <p>查不到明确结果时按指数退避排下一轮；一旦 {@code REQUEST_TMS} 距今超过
     * {@code giveUpAfterHours}，置 {@link #STATUS_MANUAL} 并打 ERROR，
     * <b>NEVER 靠次数用尽让它静默掉出扫描范围</b>。</p>
     *
     * @return true 表示本轮已收口到终态（无需再扫），false 表示状态仍不明、已排下一轮
     */
    public boolean reconcileRefund(F2fRefund refund) {
        String refundNo = refund.getRefundNo();
        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getRefundQueryUrl(),
                messageFactory.buildRefundQueryRequest(refundNo));

        if (result.isTransportFailed() || !result.isSuccessCode()) {
            String reason = result.isTransportFailed() ? result.getFailureReason() : result.getMsg();
            if (giveUpToManual(refund, reason)) {
                return true;
            }
            refundMapper.increaseRetryTimes(refundNo, truncate(reason),
                    nextQueryTms(refund.getRetryTimes()));
            log.warn("退款收口未拿到有效结果，本轮不动状态, refundNo={}, transportFailed={}",
                    refundNo, result.isTransportFailed());
            return false;
        }

        PayCenterStatus status = result.status();
        LocalDateTime now = LocalDateTime.now();
        if (status == PayCenterStatus.SUCCESS) {
            int updated = refundMapper.updateStatus(refundNo, PENDING_STATUSES, STATUS_SUCCESS,
                    result.string("refundOrderNo"), now);
            log.info("退款收口为成功, refundNo={}, updatedRows={}", refundNo, updated);
            refreshOrderRefundSummary(refund);
            return true;
        }
        if (status != null && status.isFailed()) {
            int updated = refundMapper.updateStatus(refundNo, PENDING_STATUSES, STATUS_FAILED,
                    result.string("refundOrderNo"), now);
            log.error("退款收口为失败，需人工介入, refundNo={}, updatedRows={}", refundNo, updated);
            refreshOrderRefundSummary(refund);
            return true;
        }
        String pending = "支付中心仍报 " + status;
        if (giveUpToManual(refund, pending)) {
            return true;
        }
        refundMapper.increaseRetryTimes(refundNo, truncate(pending),
                nextQueryTms(refund.getRetryTimes()));
        log.info("退款收口时{}，按退避排下一轮, refundNo={}", pending, refundNo);
        return false;
    }

    /**
     * 退款单收口到终态后，重算订单上的退款汇总三列。
     *
     * <p><b>重算式、不是累加式</b>：{@code updateRefundSummary} 直接从 {@code F2F_REFUND} 里
     * {@code REFUND_STATUS='SUCCESS'} 的行 {@code SUM} 出金额，因此执行 N 次结果相同、
     * 天然幂等，不需要 CAS、也不需要前置状态白名单。累加写法（{@code REFUND_AMOUNT = REFUND_AMOUNT + ?}）
     * 在并发或补跑下会算出偏小 / 虚高的值，虚高之后真实退款会被误判成超额——
     * 那个坑记在 {@code PayTxnDetailMapper.xml} 的注释里，<b>NEVER 改回累加</b>。</p>
     *
     * <p><b>NEVER 动 {@code ORDER_STATUS}</b>：退款与支付/履约主状态正交（ADR-D88）。
     * 返回值只进日志，<b>NEVER 拿它判断退款成败</b>——0 行只说明订单号对不上，
     * 而退款单的终态已经落库了。</p>
     */
    private void refreshOrderRefundSummary(F2fRefund refund) {
        String origOrderNo = refund.getOrigOrderNo();
        if (origOrderNo == null || origOrderNo.isBlank()) {
            log.error("退款单没有原订单号，无法重算订单退款汇总, refundNo={}", refund.getRefundNo());
            return;
        }
        int updated = orderMapper.updateRefundSummary(origOrderNo);
        log.info("订单退款汇总已重算, orderNo={}, refundNo={}, updatedRows={}",
                origOrderNo, refund.getRefundNo(), updated);
    }

    /**
     * 次数用尽或悬挂超过时间窗就置 {@link #STATUS_MANUAL}，停止自动收口并留 ERROR 告警。
     *
     * <p>两个判据谁先到算谁：{@code RETRY_TIMES >= maxQueryTimes}，
     * 或 {@code REQUEST_TMS} 距今超过 {@code giveUpAfterHours}。
     * 时间窗取一个支付中心对账周期，超出后继续查也拿不到新信息，该由人工或对账文件兜底；
     * 次数上限是兜底，防止退避参数被改小后查询次数失控。</p>
     *
     * <p><b>这个判定 MUST 留在 application 层</b>：搬进扫表谓词就变成「次数一到直接从
     * 扫描结果消失」，状态停在非终态且无人知晓，那正是原实现的缺陷。
     * {@code REQUEST_TMS} 为空时只按次数判，不因缺时间戳就永不放弃。</p>
     *
     * @return true 表示已转人工终态，调用方 MUST 直接返回、NEVER 再累加重试
     */
    private boolean giveUpToManual(F2fRefund refund, String reason) {
        LocalDateTime requestTms = refund.getRequestTms();
        int queried = refund.getRetryTimes() == null ? 0 : refund.getRetryTimes();
        boolean timesExhausted = queried >= maxQueryTimes;
        boolean windowExpired = requestTms != null
                && requestTms.isBefore(LocalDateTime.now().minusHours(giveUpAfterHours));
        if (!timesExhausted && !windowExpired) {
            return false;
        }
        String refundNo = refund.getRefundNo();
        int updated = refundMapper.updateStatus(refundNo, PENDING_STATUSES, STATUS_MANUAL,
                null, LocalDateTime.now());
        log.error("退款收口放弃自动判定，转人工介入（钱是否已退未知，MUST 查支付中心或走对账）,"
                        + " refundNo={}, 次数用尽={}, 超时间窗={}, requestTms={}, queried={}/{},"
                        + " 时间窗={}小时, lastReason={}, updatedRows={}",
                refundNo, timesExhausted, windowExpired, requestTms, queried, maxQueryTimes,
                giveUpAfterHours, reason, updated);
        return true;
    }

    /**
     * 按已失败次数算下次查询时刻：间隔 {@code base * 2^n}，封顶 {@code max}。
     *
     * <p>默认 300s 起、3600s 封顶，即 5 / 10 / 20 / 40 分钟后转为每小时一次；
     * 按此序列约 27 次覆盖满 24 小时。移位次数钳到 20 以内，避免 {@code long} 溢出成负数。</p>
     */
    private LocalDateTime nextQueryTms(Integer failedTimes) {
        int n = failedTimes == null || failedTimes < 0 ? 0 : Math.min(failedTimes, 20);
        long delay = Math.min(backoffBaseSeconds << n, backoffMaxSeconds);
        return LocalDateTime.now().plusSeconds(delay);
    }

    /** 扫一批到点该查的退款单，供定时任务调用。 */
    public List<F2fRefund> loadRetryCandidates(LocalDateTime now, int limit) {
        return refundMapper.selectRetryCandidates(PENDING_STATUSES, now, limit);
    }

    /** 按退款单号查询，供运营端与通知链路使用。 */
    public F2fRefund findByRefundNo(String refundNo) {
        return refundMapper.selectByRefundNo(refundNo);
    }

    /**
     * 撞唯一索引后回查已有退款单。
     *
     * <p>按 {@code (ticketLogicNum, refundSource)} 在同一原订单的退款单里定位——
     * 这两列加上原订单号正是 {@code UK_F2F_REFUND_IDEM} 的三要素。
     * {@code ticketLogicNum} 为空表示整单退，索引里用 {@code #WHOLE#} 占位，
     * 这里用 {@link Objects#equals} 对齐 null 语义。</p>
     */
    private F2fRefund findExisting(RefundCommand command) {
        List<F2fRefund> refunds = refundMapper.selectByOrigOrderNo(command.origOrderNo());
        for (F2fRefund candidate : refunds) {
            if (Objects.equals(candidate.getTicketLogicNum(), command.ticketLogicNum())
                    && command.refundSource().equals(candidate.getRefundSource())) {
                return candidate;
            }
        }
        return null;
    }

    /** {@code FAIL_REASON} 列宽 512，超长截断由应用负责（mapper 文档已注明）。 */
    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 512 ? reason : reason.substring(0, 512);
    }
}
