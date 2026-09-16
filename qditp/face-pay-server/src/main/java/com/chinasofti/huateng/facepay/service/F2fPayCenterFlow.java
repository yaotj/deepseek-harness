package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatusTransition;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 「与支付中心交互并收口本地状态」这条骨架的唯一实现（模板方法 + 结果策略）。
 *
 * <p>重构前，同一条骨架在 4 个 service 里各抄一遍预下单、3 个 service 里各抄一遍查询收口：
 * {@code F2fTvmOrderService.preOrderAtPayCenter} / {@code F2fTopupService.preOrderAtPayCenter} /
 * {@code F2fAppOrderService.requestPayInfo} / {@code F2fScanPayService.payAtPayCenter}，
 * 以及 {@code queryAtPayCenter} / {@code resolvePayResult} / {@code resolve}。
 * 判定序列逐行相同，<b>差别只在「用哪个响应壳回话」</b>。
 *
 * <p><b>本类只管状态推进，绝不组装响应。</b> 四个渠道的 retCode 族互不相同
 * （TVM {@code 0000/2002/2999}、BOM {@code 0000/8003/8006/8999}、APP {@code 0000/9999}），
 * 统一它们是明确禁止的。因此本类返回 {@link Submitted} / {@link Settled} 这类<b>判定结果</b>，
 * 由调用方 {@code switch} 模式匹配后各自组壳 —— 少写一个分支直接编译失败。
 *
 * <p><b>本类不是「统一 CAS 入口」</b>（{@code docs/domain/state-machines.md} §二③ 明令禁止）。
 * 它只承担「支付中心答复所蕴含的那几步推进」：受理→{@code PAYING}、收款→{@code PAID}、
 * 被拒→{@code PAY_FAILED}、未支付→{@code EXPIRED}。退款域（{@code REFUNDING} /
 * {@code REFUNDED}）与履约域（{@code FULFILL_FAILED}）的 CAS <b>仍留在各自 service</b>，
 * NEVER 挪进来 —— 那些转移的副作用与冲突口径按链路不同，合并即失去分辨力。
 *
 * <p>本类<b>不带 {@code @Transactional}</b>，且 MUST 保持如此：中间那次
 * {@code execute} 是网络调用，被事务包住会把行锁持有时长拉到对端响应时长（AGENTS.md §5.2）。
 */
@Service
public class F2fPayCenterFlow {

    private static final Logger log = LoggerFactory.getLogger(F2fPayCenterFlow.class);

    /** 支付流水的初始态，{@code markFinalStatus} 的 CAS 前置。 */
    private static final List<String> PAYMENT_INIT = List.of("INIT");

    /**
     * 受理成功后推 {@code PAYING} 的 CAS 前置，只认 {@code CREATED}。
     *
     * <p>拆成常量不是为了好看：那条 CAS <b>必须与 {@code // CAS-DISCARD:} 标记同行</b>，
     * 否则 {@code F2fOrderStatusArchTest.casReturnValueNeverDropped} 只看得见调用行、
     * 看不见下一行的标记，直接判成「返回值被丢弃」（2026-09-14 实测被门禁拦下）。</p>
     */
    private static final List<String> PAYABLE_FROM_CREATED = List.of(F2fOrderStatus.CREATED.name());

    /** 「已发起支付」的记账态。 */
    private static final String PAYING = F2fOrderStatus.PAYING.name();

    /**
     * IF8B-05 {@code payDate} 的格式。
     *
     * <p><b>规格原文没给格式</b>，此处对齐同族 IF8B-02 的 {@code terminationTime}
     * （{@code YYYYMMDDHH24mmss}），<b>待甲方确认</b>。改这个常量等于改对外契约，
     * MUST 先和 APP 侧对齐。</p>
     */
    private static final DateTimeFormatter PAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 设备单（{@code THIRD_USER_ID} 为空）推 IF8B-05 时的重试上限：<b>一次即终态</b>。
     *
     * <p>理由与「NEVER 借它跳过入队」的边界写在
     * {@code F2fNotifyService.enqueue(..., int maxRetryTimes)} 的方法注释里，改这里 MUST 一起看。</p>
     */
    private static final int NO_USER_ID_NOTIFY_RETRY = 1;

    private final PayCenterClient payCenterClient;

    private final PayCenterMessageFactory messageFactory;

    private final F2fPaymentMapper paymentMapper;

    private final F2fOrderMapper orderMapper;

    private final F2fNotifyService notifyService;

    public F2fPayCenterFlow(PayCenterClient payCenterClient,
                            PayCenterMessageFactory messageFactory,
                            F2fPaymentMapper paymentMapper,
                            F2fOrderMapper orderMapper,
                            F2fNotifyService notifyService) {
        this.payCenterClient = payCenterClient;
        this.messageFactory = messageFactory;
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.notifyService = notifyService;
    }
    /**
     * 被支付中心拒绝时的订单侧推进策略。
     *
     * <p><b>{@code null} 表示不推进，这是有意的取值</b>：APP 侧被拒后订单要留在
     * {@code CREATED}，乘客可以换个支付通道再来一次；TVM / BOM 侧一次被拒即置
     * {@code PAY_FAILED}。重构前这个差异<b>只体现为「某个类里少了两行」</b>，
     * 现在必须显式写出来。</p>
     *
     * @param fromStatuses CAS 的前置状态白名单
     * @param reasonPrefix 落到 {@code ORDER_STATUS} 变更原因里的前缀，后面拼支付中心 code
     */
    public record RejectTransition(List<String> fromStatuses, String reasonPrefix) {
    }

    /** 预下单/扣款的入参。 */
    public record SubmitSpec(String orderNo,
                             int attemptNo,
                             PayCenterRequest message,
                             String dataAlias,
                             RejectTransition rejectTransition,
                             boolean inspectSyncStatus,
                             String scene) {

        /** {@code dataAlias} 非空即要求应答里的 {@code data} 有值（二维码串 / 支付信息）。 */
        public boolean dataRequired() {
            return dataAlias != null;
        }
    }

    /** 预下单/扣款的判定结果，调用方 MUST 穷尽分支。 */
    public sealed interface Submitted {

        /** 已受理：支付流水置 {@code PROCESSING}、订单已尝试推 {@code PAYING}。 */
        record Accepted(PayCenterResult result) implements Submitted {
        }

        /** 明确被拒：支付流水置 {@code FAILED}，订单按 {@link RejectTransition} 处理。 */
        record Rejected(PayCenterResult result) implements Submitted {
        }

        /**
         * 状态不明：支付流水置 {@code UNKNOWN}，<b>订单一律不动</b>，留给收口任务。
         *
         * <p>两种成因合并在这里，调用方要区分就看 {@code result.isTransportFailed()}：
         * ① 对端没答上来；② 答了 {@code code=0} 但 {@code data} 是空的。</p>
         */
        record Unknown(PayCenterResult result, String reason) implements Submitted {
        }

        /**
         * 同步已收款（只有付款码会走到）。
         *
         * <p><b>本类刻意什么都没写</b>：支付流水的成功行各渠道列不同，
         * 由调用方写完再调 {@link #markPaidAndReport(String)}。</p>
         */
        record SyncPaid(PayCenterResult result) implements Submitted {
        }
    }
    /**
     * 预下单 / 扣款：发往支付中心，按应答收口支付流水与订单状态。
     *
     * <p>判定顺序<b>不可调换</b>：传输失败 → 业务码非 0 → （可选）同步支付状态 →
     * （可选）{@code data} 为空 → 受理成功。</p>
     *
     * <p><b>调用方 MUST 自己先 INSERT 一行 {@code INIT} 的支付流水</b>（各渠道列不同），
     * 本方法只负责把它推向终态。</p>
     */
    public Submitted submit(SubmitSpec spec) {
        String orderNo = spec.orderNo();
        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getPayUrl(), spec.message());

        if (result.isTransportFailed()) {
            markPayment(spec, "UNKNOWN", null, result.getFailureReason(), result);
            log.error("{} 支付中心未明确应答，订单不推进等收口, orderNo={}, reason={}",
                    spec.scene(), orderNo, result.getFailureReason());
            return new Submitted.Unknown(result, result.getFailureReason());
        }
        if (!result.isSuccessCode()) {
            markPayment(spec, "FAILED", result.getCode(), result.getMsg(), result);
            pushPayFailed(spec, result.getCode());
            log.warn("{} 被支付中心拒绝, orderNo={}, code={}, msg={}",
                    spec.scene(), orderNo, result.getCode(), result.getMsg());
            return new Submitted.Rejected(result);
        }
        if (spec.inspectSyncStatus()) {
            PayCenterStatus payStatus = result.status();
            if (payStatus == PayCenterStatus.SUCCESS) {
                return new Submitted.SyncPaid(result);
            }
            if (payStatus != null && payStatus.isFailed()) {
                markPayment(spec, "FAILED", result.getCode(), result.getMsg(), result);
                pushPayFailed(spec, result.getCode());
                log.info("{} 支付中心返回支付失败, orderNo={}", spec.scene(), orderNo);
                return new Submitted.Rejected(result);
            }
        }
        if (spec.dataRequired()) {
            String data = result.string("data");
            if (data == null || data.isBlank()) {
                String reason = "受理成功但未返回" + spec.dataAlias();
                markPayment(spec, "UNKNOWN", result.getCode(), reason, result);
                log.error("{} 支付中心 code=0 但 data 为空，按 UNKNOWN 处理, orderNo={}",
                        spec.scene(), orderNo);
                return new Submitted.Unknown(result, reason);
            }
        }
        markPayment(spec, "PROCESSING", result.getCode(), result.getMsg(), result);
        orderMapper.updateStatus(orderNo, PAYABLE_FROM_CREATED, PAYING, null); // CAS-DISCARD: PAYING 是「已发起支付」记账标记，写不上不改变任何后续判断
        log.info("{} 支付中心已受理, orderNo={}, payCenterOrderNo={}",
                spec.scene(), orderNo, result.string("orderNo"));
        return new Submitted.Accepted(result);
    }

    private void markPayment(SubmitSpec spec, String payStatus, String code, String msg,
                             PayCenterResult result) {
        paymentMapper.markFinalStatus(spec.orderNo(), spec.attemptNo(), PAYMENT_INIT, payStatus,
                code, msg, (int) result.getCostMs(), LocalDateTime.now());
    }

    private void pushPayFailed(SubmitSpec spec, String payCenterCode) {
        RejectTransition transition = spec.rejectTransition();
        if (transition == null) {
            return;
        }
        warnIfConflict(spec.orderNo(),
                orderMapper.updateStatus(spec.orderNo(), transition.fromStatuses(),
                        F2fOrderStatus.PAY_FAILED.name(), transition.reasonPrefix() + payCenterCode),
                F2fOrderStatus.PAY_FAILED, spec.scene());
    }
    /** 查询收口的判定结果。四个取值互斥且穷尽，调用方 {@code switch} 上不写 default 也能编译。 */
    public enum Settlement {
        /** 支付中心明确已收款，本地已尝试推 {@code PAID}。 */
        PAID,
        /** 支付中心明确支付失败，本地已尝试推 {@code PAY_FAILED}。 */
        FAILED,
        /** 支付中心明确未支付，本地已尝试推 {@code EXPIRED}。 */
        UNPAID,
        /**
         * 没问出结论（传输失败 / 业务码非 0 / 状态仍在处理中）。
         * <p><b>本地一律不动状态</b>，调用方 MUST 回「支付中 / 处理中」让对方继续轮询，
         * NEVER 回失败 —— 钱可能已经收了。</p>
         */
        PENDING
    }

    /** @param result 原始应答，供调用方取 {@code paymentVendor} / {@code channelOrderNo} 等渠道字段 */
    public record Settled(Settlement settlement, PayCenterResult result) {
    }

    /**
     * 查询收口的入参。
     *
     * @param onPaid 查到已收款时<b>先</b>执行的支付流水回写（各渠道列不同，故为策略回调）；
     *               本类随后才做 {@code markPaid} + 冲突上报，顺序 MUST 保持
     * @param expireOnUnpaid 「问不到就当没付过」策略，<b>null 表示不启用（设备 / APP 查询链路的默认口径）</b>。
     *                       非 null 时把「业务码非 0」与「明确支付失败」都并入 {@code UNPAID} 并置
     *                       {@code EXPIRED} —— 这是<b>过期收口专用</b>的判定，2026-09-16 收口进来（P3）。
     *                       <b>NEVER 给设备查询链路传非 null</b>：那条链路上业务码非 0 只能回「支付中」，
     *                       置终态等于把可能已收款的单判死。
     * @param onPaidNotify 收款后的<b>自定义收口</b>，null 表示走本类的 {@link #markPaidAndReport(String)}。
     *                     非 null 时本类<b>不碰订单状态</b>，由回调自己 {@code markPaid} + 入队 IF8B-05；
     *                     入参是原始应答与本次收口时刻（<b>MUST 用它去写 {@code PAID_TMS} 与通知的
     *                     {@code payDate}，两处同一个值</b>）。加这个钩子的唯一理由是过期收口那支的
     *                     日志与 {@code tradeNo} 取值都与 {@code markPaidAndReport} 不同，
     *                     见 {@link F2fOrderExpireService#reconcileExpiredOrder}。
     */
    public record SettleSpec(String orderNo,
                             List<String> fromStatuses,
                             Consumer<PayCenterResult> onPaid,
                             String scene,
                             ExpireOnUnpaid expireOnUnpaid,
                             BiConsumer<PayCenterResult, LocalDateTime> onPaidNotify) {

        /** 设备 / APP 查询链路用的短构造：不启用过期判定、收款后走 {@code markPaidAndReport}。 */
        public SettleSpec(String orderNo, List<String> fromStatuses,
                          Consumer<PayCenterResult> onPaid, String scene) {
            this(orderNo, fromStatuses, onPaid, scene, null, null);
        }
    }

    /**
     * 过期收口把「未支付」落库时用的两句变更原因。
     *
     * <p>拆成两个字段而不是一句话：两种成因的处置相同但<b>诊断价值不同</b> ——
     * 「支付中心根本没有这笔单」意味着预下单就没成功，「明确未支付/失败」意味着码发出去了没人付。
     * 2026-09-10 那次「订单每 30 秒外呼、持续 40 分钟没有出口」的缺陷就是靠前者定位的，
     * <b>NEVER 合并成一句</b>。
     *
     * @param bizErrorReason 对端答上来但业务码非 0（实测 9999「未找到数据」）时写入的原因
     * @param unpaidReason   对端明确报未支付 / 支付失败时写入的原因
     */
    public record ExpireOnUnpaid(String bizErrorReason, String unpaidReason) {
    }

    /** 向支付中心查实际结果并收口本地状态。<b>必须在事务外</b>。 */
    public Settled settle(SettleSpec spec) {
        String orderNo = spec.orderNo();
        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getQueryUrl(), messageFactory.buildQueryRequest(orderNo));

        if (result.isTransportFailed() || (!result.isSuccessCode() && spec.expireOnUnpaid() == null)) {
            log.warn("{} 查询支付中心未得到有效结果，按处理中返回, orderNo={}, transportFailed={}, code={}, reason={}",
                    spec.scene(), orderNo, result.isTransportFailed(), result.getCode(), result.getFailureReason());
            return new Settled(Settlement.PENDING, result);
        }
        if (!result.isSuccessCode()) {
            // 只有启用了 expireOnUnpaid 才会走到这里。对端答上来了、业务码非 0：支付中心没有这笔单，
            // 等价于「用户从未支付」，按 EXPIRED 收口。这正是 PayCenterResult 类注释里
            // 「业务失败：code != 0，对端明确拒绝，可判 FAILED」那一条。
            // NEVER 退回「当作没拿到结果、下轮再试」——2026-09-10 实测该分支让订单
            // F200202609100914540082 每 30 秒外呼一次、持续 40 分钟没有出口。
            pushExpired(spec, spec.expireOnUnpaid().bizErrorReason(), "支付中心无此订单");
            log.warn("{} 支付中心明确无此订单，置 EXPIRED, orderNo={}, code={}, msg={}",
                    spec.scene(), orderNo, result.getCode(), result.getMsg());
            return new Settled(Settlement.UNPAID, result);
        }
        PayCenterStatus payStatus = result.status();
        if (payStatus == PayCenterStatus.SUCCESS) {
            spec.onPaid().accept(result);
            if (spec.onPaidNotify() == null) {
                markPaidAndReport(orderNo);
                log.info("{} 查询到支付成功, orderNo={}", spec.scene(), orderNo);
            } else {
                spec.onPaidNotify().accept(result, LocalDateTime.now());
            }
            return new Settled(Settlement.PAID, result);
        }
        boolean failed = payStatus != null && payStatus.isFailed();
        if (failed && spec.expireOnUnpaid() == null) {
            warnIfConflict(orderNo, orderMapper.updateStatus(orderNo, spec.fromStatuses(),
                            F2fOrderStatus.PAY_FAILED.name(), "支付中心返回支付失败"),
                    F2fOrderStatus.PAY_FAILED, spec.scene() + " 查询到支付失败");
            log.info("{} 查询到支付失败, orderNo={}", spec.scene(), orderNo);
            return new Settled(Settlement.FAILED, result);
        }
        if (failed || payStatus == PayCenterStatus.UNPAID) {
            // 启用 expireOnUnpaid 时「支付失败」也并入这一支：过期的单没有「支付失败」这个出口，
            // 它要的是 EXPIRED（原 reconcileExpiredOrder 的口径，NEVER 改成 PAY_FAILED）。
            pushExpired(spec, spec.expireOnUnpaid() == null
                    ? "支付中心返回未支付" : spec.expireOnUnpaid().unpaidReason(), "查询到未支付");
            log.info("{} 查询到未支付，置 EXPIRED, orderNo={}, payCenterStatus={}",
                    spec.scene(), orderNo, payStatus);
            return new Settled(Settlement.UNPAID, result);
        }
        return new Settled(Settlement.PENDING, result);
    }

    private void pushExpired(SettleSpec spec, String reason, String sceneSuffix) {
        warnIfConflict(spec.orderNo(), orderMapper.updateStatus(spec.orderNo(), spec.fromStatuses(),
                        F2fOrderStatus.EXPIRED.name(), reason),
                F2fOrderStatus.EXPIRED, spec.scene() + " " + sceneSuffix);
    }

    /**
     * 推 {@code PAID} 并记「已收款但订单不在可支付状态」。
     *
     * <p>{@code markPaid} 的 WHERE 是 {@code ORDER_STATUS IN ('CREATED','PAYING')}，
     * 返 0 行意味着订单已被别人推走 —— 最常见是被 {@code EXPIRED} 收口任务抢先，
     * 而钱已经在支付中心收掉了。</p>
     *
     * <p><b>只告警、不改对上游的应答</b>：设备侧拿到失败会重新收款，那才是真的二次扣款。
     * 关键字 {@code F2F CAS 冲突} 供日志告警检索，<b>NEVER 改措辞、NEVER 降级成 warn</b>。</p>
     */
    public void markPaidAndReport(String orderNo) {
        markPaidAndReport(orderNo, null);
    }

    /**
     * 同上，并额外把支付中心订单号带进 IF8B-05 通知。
     *
     * <p>加这个重载而<b>不是改原签名</b>：原签名有 {@code F2fScanPayService} 与本类
     * {@link #settle} 两个调用方、外加 3 处注释引用，那两条链路拿不到 {@code tradeNo}。</p>
     *
     * @param tradeNo 支付中心订单号，拿不到时传 null（通知里上送空串）
     */
    public void markPaidAndReport(String orderNo, String tradeNo) {
        LocalDateTime now = LocalDateTime.now();
        F2fOrderStatusTransition.Result transit = F2fOrderStatusTransition.classify(
                orderMapper.markPaid(orderNo, now),
                F2fOrderStatus.PAID, () -> orderMapper.selectOrderStatus(orderNo));
        if (transit.conflict()) {
            log.error("F2F CAS 冲突 已收款但订单状态未推进到 PAID，需人工核对是否应退款, orderNo={}, observed={}",
                    orderNo, transit.observedStatus());
        }
        enqueuePayResultNotify(orderNo, tradeNo, now);
    }

    /**
     * IF8B-05 支付结果通知（我方 → APP_SERVER）的<b>生产者</b>：只入队，不投递。
     *
     * <p>投递由 {@code F2fNotifyJob} 扫 {@code F2F_NOTIFY_TASK} 完成，
     * 幂等靠 {@code UK_F2F_NOTIFY_IDEM}，重复调用只会命中索引返回 false。</p>
     *
     * <p><b>整个方法体被 try/catch 包住且只记日志</b>：本方法挂在支付成功收口之后，
     * 抛出去会让支付回调对支付中心报错、引来重推 —— 而钱已经收了、订单已经 PAID，
     * 重推除了放大流量没有任何用。通知漏了可由人工补，NEVER 让它打断支付主链路应答。</p>
     *
     * <p><b>推送范围 = 所有订单，故意不做 {@code TRANS_TYPE='03'} 过滤</b>
     * （用户裁决）。这与同模块 {@code F2fTicketIssueService.enqueueAppNotify}
     * 「只推 APP 单」的口径<b>故意不同</b>，<b>NEVER 顺手加上 TRANS_TYPE 过滤</b>。</p>
     *
     * <p><b>但设备单的重试上限压到 1 次</b>（2026-09-16 用户裁决，ADR-D112）：设备单
     * {@code THIRD_USER_ID} 为空 ⇒ {@code userId} 上送 null ⇒ APP 侧定位不到用户，
     * 实测**必然** {@code 7004}（`F2F_NOTIFY_TASK` 里 18 条无 {@code userId} 的 `PAY_RESULT`
     * 无一例外；唯一 SUCCESS 那条是带真实 {@code userId} 的 APP 单）。默认 5 次只是把同一条
     * 注定失败的报文推 5 遍，还会刷一条 ERROR 级 GIVEUP 日志淹掉真正要人工看的失败。
     * <b>这只压缩重试次数、不改推送范围</b>——任务照样落库，NEVER 借它退化成「设备单不推」。</p>
     *
     * @param tradeNo 支付中心订单号；null 时上送空串
     * @param paidTms 支付成功时刻，<b>MUST 由调用方传入刚刚写进 {@code PAID_TMS} 的那个值</b>。
     *                <b>NEVER 改成读回查出来的 {@code order.getPaidTms()}</b> —— 三个调用点
     *                传进来的都是各自 {@code markPaid} 用的同一个 {@code LocalDateTime}，
     *                而回查实体在并发/幂等重入下可能还是旧值或 null，两者对不上就等于
     *                给 APP 报了一个错的支付时间。
     */
    public void enqueuePayResultNotify(String orderNo, String tradeNo, LocalDateTime paidTms) {
        try {
            F2fOrder order = orderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                log.error("IF8B-05 支付结果通知入队失败：订单不存在, orderNo={}", orderNo);
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", order.getThirdUserId());
            payload.put("orderNo", order.getOrderNo());
            payload.put("tradeNo", tradeNo == null ? "" : tradeNo);
            // 规格 payResult 有 SUCCESS / FAIL 两个取值，但当前只在支付成功收口处推、
            // 没有推失败的业务需求；要加 MUST 单独定触发点，NEVER 在本方法里加参数分叉。
            payload.put("payResult", "SUCCESS");
            // 规格未写金额单位，此处按分，与同族 IF8B-04 的 refundAmount「单位分」对齐，待甲方确认。
            payload.put("payAmount", order.getOrderAmount() == null ? "" : String.valueOf(order.getOrderAmount()));
            payload.put("payDate", paidTms == null ? "" : PAY_DATE_FORMATTER.format(paidTms));
            // voucher 恒为空串：IF8B-05 在「支付成功时刻」触发，而 face-pay 的取票凭证
            // （票逻辑卡号）要等出票结果上报（IF2A-04 之后）才存在，F2F_ORDER 上没有任何
            // 可用作 voucher 的列，因此支付时刻必然为空。
            // 规格原文对 voucher 的说明自相矛盾（既写「整段用于生成二维码」又写「预留字段」），
            // 且未给必填性，当前按空串上送，待甲方澄清。
            payload.put("voucher", "");
            // orderType 固定 "0"：全推（购票单与充值单都推），而充值单在规格的 orderType
            // 枚举（0 单程票 / 1 普通日票 / 2 全城通日票）里没有对应取值，当前一并填 0，待甲方澄清。
            payload.put("orderType", "0");

            boolean hasUserId = order.getThirdUserId() != null && !order.getThirdUserId().isBlank();
            boolean enqueued = hasUserId
                    ? notifyService.enqueue(F2fNotifyService.TYPE_PAY_RESULT, orderNo, null, payload)
                    : notifyService.enqueue(F2fNotifyService.TYPE_PAY_RESULT, orderNo, null, payload,
                            NO_USER_ID_NOTIFY_RETRY);
            log.info("IF8B-05 支付结果通知入队完成, orderNo={}, tradeNo={}, enqueued={}, hasUserId={}",
                    orderNo, tradeNo, enqueued, hasUserId);
        } catch (RuntimeException e) {
            log.error("IF8B-05 支付结果通知入队异常，已吞掉不影响支付收口, orderNo={}", orderNo, e);
        }
    }

    /**
     * 记「置失败态的 CAS 没命中」。返 0 行的常见含义是并发回调已把订单推成 {@code PAID}，
     * 而调用方仍会按原口径对上游回失败 —— 这个分歧原本没有任何痕迹。
     *
     * <p><b>刻意只告警、不改应答</b>：升级成「按库里真实状态应答」是对外行为变更，
     * MUST 先有 WARN 频次数据（对齐过闸链路的观察期口径）。</p>
     */
    public void warnIfConflict(String orderNo, int updatedRows, F2fOrderStatus target, String scene) {
        F2fOrderStatusTransition.Result transit = F2fOrderStatusTransition.classify(
                updatedRows, target, () -> orderMapper.selectOrderStatus(orderNo));
        if (transit.conflict()) {
            log.warn("F2F CAS 冲突 {} 未推进到 {}，仍按原口径应答, orderNo={}, observed={}",
                    scene, target, orderNo, transit.observedStatus());
        }
    }
}
