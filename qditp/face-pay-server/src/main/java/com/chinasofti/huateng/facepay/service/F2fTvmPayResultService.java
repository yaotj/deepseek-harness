package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.api.paycenter.PayCenterResponses;
import com.chinasofti.huateng.facepay.api.paycenter.PayNoticeReqDTO;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.domain.F2fOrderRefundStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * TVM / BOM 单程票的<b>支付结果侧</b>：设备查询、支付中心回调、收银台反查订单详情。
 *
 * <p>2026-09-16 从 {@code F2fTvmOrderService}（616 行）拆出。原类同时承担
 * 「下单」「查结果」「过期收口」三件事，依赖 9 个；拆开后本类只依赖 4 个。
 * <b>URL、retCode 族与响应键集一行未改</b>，方法体逐字搬迁。</p>
 *
 * <h2>三条不可违反的编排约束（与原类一致）</h2>
 * <ol>
 *   <li><b>整个类不带 {@code @Transactional}</b>：链路里有支付中心调用，事务包住网络调用会把行锁
 *       持有时长拉长到对端响应时长（AGENTS.md §5.2 的 2026-08-26 生产事故）。</li>
 *   <li><b>对端没答上来时 NEVER 把订单写成 PAY_FAILED</b>：钱可能已经扣了。</li>
 *   <li><b>状态判断用白名单</b>：只有明确列举的状态才短路，其余一律去查支付中心。</li>
 * </ol>
 */
@Service
public class F2fTvmPayResultService {

    private static final Logger log = LoggerFactory.getLogger(F2fTvmPayResultService.class);

    /** 业务类型：充值。与 {@code F2fTopupService.BIZ_TOPUP} 同值，只用于订单详情分流。 */
    private static final String BIZ_TOPUP = "02";

    private static final String STATUS_PAY_FAILED = F2fOrderStatus.PAY_FAILED.name();

    /**
     * 对设备口径为「已收款」的内部状态白名单。退款中/已退款也曾收款成功。
     *
     * <p>里面仍留着 {@code REFUNDING} / {@code REFUNDED} 两个取值 —— 主状态自 ADR-D88
     * 起不再推进到这两个状态，但**存量行还在**，去掉会让老单答成空串。</p>
     */
    private static final List<String> PAID_LIKE = List.of(
            F2fOrderStatus.PAID.name(), F2fOrderStatus.FULFILLED.name(), F2fOrderStatus.FULFILL_FAILED.name(),
            F2fOrderStatus.REFUNDING.name(), F2fOrderStatus.REFUNDED.name());

    /** 对设备口径为「失败」的内部状态白名单。 */
    private static final List<String> FAILED_LIKE = F2fOrderStatus.FAILED_LIKE;

    /** 需要向支付中心查实际结果的状态白名单。 */
    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    /** 收银台侧的时间格式，照搬旧 {@code TvmOrderServiceImpl.DATE_FORMATTER}。 */
    private static final DateTimeFormatter PAY_CENTER_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    /** 只用来取 {@code getPayNoticeUrl()} 回吐给收银台，本类不自己发起外呼。 */
    private final PayCenterClient payCenterClient;

    private final F2fPayCenterFlow payCenterFlow;

    public F2fTvmPayResultService(F2fOrderMapper orderMapper,
                                  F2fPaymentMapper paymentMapper,
                                  PayCenterClient payCenterClient,
                                  F2fPayCenterFlow payCenterFlow) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.payCenterClient = payCenterClient;
        this.payCenterFlow = payCenterFlow;
    }

    /**
     * 查订单。<b>只查 {@code F2F_ORDER}，没有旧库旁路</b>。
     *
     * <p>旧库只读旁路（{@code LegacyOrderReader}）已于 2026-09-13 按用户裁决整体删除
     * （原话「后续要停掉旧服务，删除旧库的，不需要做兼容层」）。连带后果：
     * <b>切流前由 {@code collect-pay-server} 建的历史单，在本服务上一律「订单不存在」</b>
     * （{@code 2002 / 没有找到匹配的订单}）。这是<b>有意的</b>，NEVER 因为
     * 「历史单查不到」再把旁路加回来。</p>
     */
    private F2fOrder loadOrderForQuery(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
    }

    /**
     * IF2A-03 查询支付结果。<b>{@code retCode} 恒为 0000</b>，业务结果在 {@code paymentResult}。
     *
     * <p>本地已是终态则短路返回；只有 {@code CREATED} / {@code PAYING} 才去问支付中心。</p>
     */
    public JSONObject queryPayResult(RequestPayResultReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = loadOrderForQuery(orderNo);
        if (order == null) {
            log.info("查询支付结果 订单不存在, orderNo={}", orderNo);
            return TvmResponses.payResultFail(DeviceRetCode.INVALID_PARAM, "没有找到匹配的订单，请确认订单号是否正确");
        }
        String status = order.getOrderStatus();
        if (PAID_LIKE.contains(status)) {
            return TvmResponses.payResult(PaymentResult.SUCCESS, channelCodeOf(orderNo));
        }
        if (FAILED_LIKE.contains(status)) {
            return TvmResponses.payResult(PaymentResult.FAILED, channelCodeOf(orderNo));
        }
        if (!PENDING.contains(status)) {
            log.warn("查询支付结果 命中未预期状态，按支付中处理, orderNo={}, status={}", orderNo, status);
            return TvmResponses.payResult(PaymentResult.ORDERED, channelCodeOf(orderNo));
        }
        return queryAtPayCenter(orderNo);
    }

    /** 向支付中心查实际结果并收口本地状态。<b>必须在事务外</b>。 */
    private JSONObject queryAtPayCenter(String orderNo) {
        F2fPayCenterFlow.Settled settled = payCenterFlow.settle(new F2fPayCenterFlow.SettleSpec(
                orderNo, PENDING,
                result -> markPaymentSuccess(orderNo, result, "查询到支付成功"),
                "TVM"));
        PayCenterResult result = settled.result();
        String vendor = result.string("paymentVendor");
        return switch (settled.settlement()) {
            case PAID -> TvmResponses.payResult(PaymentResult.SUCCESS, vendor);
            case FAILED, UNPAID -> TvmResponses.payResult(PaymentResult.FAILED, vendor);
            // 两种 PENDING 的 channelCode 取值不同，这是重构前就有的区别、不是笔误：
            // 对端没答上来时报文里没有 paymentVendor，只能回落到本地订单的渠道；
            // 答上来但状态仍在处理中时，以对端给的 paymentVendor 为准。
            case PENDING -> TvmResponses.payResult(PaymentResult.ORDERED,
                    result.isTransportFailed() || !result.isSuccessCode() ? channelCodeOf(orderNo) : vendor);
        };
    }

    /**
     * 支付结果回调（支付中心 → ITP）。<b>用 {@code merchantOrderNo} 定位本地订单</b>，
     * {@code orderNo} 是支付中心侧订单号。
     *
     * <p>应答语义：只有明确处理完才回 {@code code=0}；<b>状态不明一律回失败让对端重推</b>，
     * NEVER 回成功——那等于永久丢掉一笔支付结果。已是终态的重复回调直接回成功（幂等）。</p>
     */
    public JSONObject receivePayNotice(PayNoticeReqDTO request) {
        String orderNo = request.getMerchantOrderNo();
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("支付回调缺少 merchantOrderNo, request={}", request);
            return PayCenterResponses.fail("merchantOrderNo不能为空");
        }
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.warn("支付回调订单不存在, merchantOrderNo={}", orderNo);
            return PayCenterResponses.orderNotExist();
        }
        String status = order.getOrderStatus();
        if (PAID_LIKE.contains(status) || FAILED_LIKE.contains(status)) {
            log.info("支付回调重复到达，已是终态直接回成功, orderNo={}, status={}", orderNo, status);
            return PayCenterResponses.success();
        }

        PayCenterStatus notified = PayCenterStatus.fromCode(request.getStatus());
        Integer attemptNo = lastAttemptNo(orderNo);
        LocalDateTime now = LocalDateTime.now();
        if (notified == PayCenterStatus.SUCCESS) {
            paymentMapper.markSuccess(orderNo, attemptNo, request.getOrderNo(), request.getChannelOrderNo(),
                    request.getPaymentVendor(), PayCenterResponses.CODE_SUCCESS, "回调通知支付成功", now);
            int updated = orderMapper.markPaid(orderNo, now);
            log.info("支付回调置为已支付, orderNo={}, updatedRows={}", orderNo, updated);
            // IF8B-05 支付结果通知：本分支的语义/日志与 markPaidAndReport 不同，
            // NEVER 替换成 markPaidAndReport，只在其后追加入队。
            // tradeNo 取 request.getOrderNo()（支付中心侧订单号，上一行 markSuccess 用的就是它），
            // 时刻复用同一个 now —— 与刚写进 PAID_TMS 的值完全一致。
            payCenterFlow.enqueuePayResultNotify(orderNo, request.getOrderNo(), now);
            return PayCenterResponses.success();
        }
        if (notified != null && notified.isFailed()) {
            paymentMapper.markFinalStatus(orderNo, attemptNo, List.of("INIT", "PROCESSING"), "FAILED",
                    PayCenterResponses.CODE_SUCCESS, "回调通知支付失败", null, now);
            payCenterFlow.warnIfConflict(orderNo, orderMapper.updateStatus(orderNo, PENDING, STATUS_PAY_FAILED,
                    "回调通知支付失败"), F2fOrderStatus.PAY_FAILED, "回调通知支付失败");
            log.info("支付回调置为支付失败, orderNo={}", orderNo);
            return PayCenterResponses.success();
        }
        log.warn("支付回调状态不明确，回失败让支付中心重推, orderNo={}, status={}", orderNo, request.getStatus());
        return PayCenterResponses.fail();
    }

    /**
     * 支付中心反查 ITP 订单详情（收银台页面渲染用）。
     *
     * <p>{@code orderStatus} 的口径逐字照搬旧 {@code getPayCenterPayOrderDetailResult}：
     * {@code 1} 支付中、{@code 2} 支付成功、{@code 7} 已退款、<b>其余是空字符串</b>。
     * 空串看着像 bug，但收银台已按此解析，NEVER 改成某个码。</p>
     *
     * <p>旧实现先查 {@code TVM_PAY_PRE_ORDER} 拿 {@code transType} 再分流到购票 / 充值两套
     * 代码，<b>两套的键集并不相同</b>：充值单没有出站两个键、多一个 {@code singleTicketPrice}、
     * 且 {@code singleTicketNum} 是字符串 {@code "1"}。表合一后仍 MUST 按 {@code BIZ_TYPE}
     * 分流，NEVER 用一套键集覆盖两种业务——2026-09-11 重放对比实测：合成一套会让充值单的
     * 站码、张数、单价三项全部退化成 null，收银台页面渲染不出充值信息。</p>
     */
    public JSONObject requestPayOrderDetail(RequestPayResultReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = loadOrderForQuery(orderNo);
        if (order == null) {
            log.info("支付中心查询订单详情 订单不存在, orderNo={}", orderNo);
            // 这一支刻意保留「只有 retCode/retMsg」的裸错误体：调用方是支付中心收银台、不是设备，
            // 且购票单 13 键与充值单 12 键的键集不同，查不到订单时无法判断该回哪一套。
            // 判据与 NEVER 事项写在 TvmResponses.payOrderDetail 的类注释里。
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "没有找到匹配的订单，请确认订单号是否正确");
        }
        if (BIZ_TOPUP.equals(order.getBizType())) {
            return TvmResponses.topupOrderDetail(orderNo, order.getDeviceId(), order.getOrderAmount(),
                    format(order.getCreateTms()), payCenterOrderStatus(order),
                    format(order.getPaidTms()), payCenterClient.properties().getPayNoticeUrl());
        }
        return TvmResponses.payOrderDetail(orderNo, order.getEntryStationCode(), order.getExitStationCode(),
                order.getTicketNum(), order.getOrderAmount(), format(order.getCreateTms()),
                payCenterOrderStatus(order), format(order.getPaidTms()),
                payCenterClient.properties().getPayNoticeUrl());
    }

    /**
     * 内部状态 → 收银台口径。未列举的一律空串，与旧实现一致。
     *
     * <p><b>{@code 7}（已退款）判据是 {@code REFUND_STATUS}，不是 {@code ORDER_STATUS}</b>：
     * 退款与支付/履约主状态正交（ADR-D88），退款成功后主状态仍停在 {@code PAID} / {@code FULFILLED}。
     * 原实现写的是 {@code REFUNDED.equals(orderStatus) ? "7" : "2"}，主状态不再推进到
     * {@code REFUNDED} 之后<b>那个 7 永远出不来</b>，设备侧查询会退化成恒答「支付成功」。</p>
     *
     * <p><b>{@code PARTIAL} 与 {@code SUCCESS} 都答 7，回归旧 collect-pay 口径</b>
     * （ADR-D106，用户裁决）。旧实现的判据是 {@code TBL_TVM_ORDER_PAY.RSV2}（退款单号）非空，
     * 而 <b>部分退与全额退都会写那一列</b> —— 也就是说收银台侧的 {@code 7} 语义一直是
     * 「这单发生过退款」，不是「已全额退完」。<b>NEVER 退回只认 {@code SUCCESS}</b>：
     * 那样一笔部分退款单在旧系统答 7、在本模块答 2，切换即静默改变对外行为，
     * 若设备用它判「还能不能再退」会得出「从未退过」的反向结论。
     *
     * <p>我方内部仍<b>保留 {@code NONE} / {@code PARTIAL} / {@code SUCCESS} 三档</b>
     * （{@code F2fOrderRefundStatus}），只是在这一个出口上把后两档投影成同一个码。
     * <b>NEVER 因为对外合并就把内部也并成两值</b> —— 运营端、对账与超退闸门都依赖
     * {@code PARTIAL} 这一档；判据见 {@code F2fOrderRefundStatus} 类注释。</p>
     */
    private static String payCenterOrderStatus(F2fOrder order) {
        String orderStatus = order.getOrderStatus();
        if (PAID_LIKE.contains(orderStatus)) {
            return F2fOrderRefundStatus.refunded(order.getRefundStatus()) ? "7" : "2";
        }
        if (PENDING.contains(orderStatus)) {
            return "1";
        }
        return "";
    }

    /** {@code yyyyMMddHHmmss}，null 进 null 出——{@code payDate} 为 null 时该 key 不下发。 */
    private static String format(LocalDateTime tms) {
        return tms == null ? null : tms.format(PAY_CENTER_DATE_FORMATTER);
    }

    /** 回调要落在最近一次支付尝试上；查不到时按 1 处理（预下单必然已插过 attempt 1）。 */
    private Integer lastAttemptNo(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null || last.getAttemptNo() == null ? 1 : last.getAttemptNo();
    }

    /**
     * 据支付中心查询结果把最近一次支付尝试收口为 SUCCESS，并回填渠道码与两个外部订单号。
     *
     * <p>没有这一步时 {@code F2F_PAYMENT.PAY_CHANNEL_CODE} 永远为空，
     * 查询接口回吐的 {@code paymentChannelCode} 也就恒为 null——旧实现是写库并回吐的，
     * 这里补齐该行为。</p>
     *
     * <p>{@code markSuccess} 返回 0 属正常（该尝试已是 SUCCESS，回调先到了），只记日志不报错；
     * 并发下第二条 SUCCESS 由 {@code UK_F2F_PAY_SUCCESS} 抛 {@link DuplicateKeyException}，
     * 同样按「已有成功记录」幂等吞掉。<b>NEVER 让这里的异常打断订单状态推进</b>——
     * 支付确实成功了，订单必须置为已支付。</p>
     *
     * <p><b>本方法是 public，只为给 {@link F2fOrderExpireService} 复用</b>：过期收口查到
     * 「其实已支付」时要做的落库与这里逐字相同。2026-09-16 拆分时刻意<b>不</b>抄第二份副本
     * —— 那 15 行含幂等语义与 cause 链判定，抄一份就多一处会漂移的地方。
     * <b>NEVER 把它挪进 {@code F2fPayCenterFlow}</b>：flow 的 {@code SettleSpec} 已把
     * 「收款后做什么」定义成调用方传入的 {@code Consumer}，挪进去等于让 flow 反过来知道
     * TVM 链路的落库细节。</p>
     */
    public void markPaymentSuccess(String orderNo, PayCenterResult result, String retMsg) {
        try {
            int updated = paymentMapper.markSuccess(orderNo, lastAttemptNo(orderNo),
                    result.string("orderNo"), result.string("channelOrderNo"),
                    result.string("paymentVendor"), PayCenterResponses.CODE_SUCCESS, retMsg,
                    LocalDateTime.now());
            if (updated == 0) {
                log.info("支付尝试已是终态，无需再置成功, orderNo={}", orderNo);
            }
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("该订单已有成功的支付尝试，幂等跳过, orderNo={}", orderNo);
        }
    }

    /**
     * 取该订单最近一次支付尝试的渠道码，用于回吐 {@code paymentChannelCode}。
     *
     * <p>渠道码要等支付回调或查询结果才有值，因此这里可能是 {@code null}——旧实现同样会回 null，
     * 不做兜底填值。</p>
     */
    private String channelCodeOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayChannelCode();
    }
}
