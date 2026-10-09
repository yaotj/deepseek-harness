package com.chinasofti.huateng.gatetxnpay.paysign;

import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 出站扣费的唯一出账口：组报文调支付域，并把结果收敛成 {@code DEBIT_STATUS}。
 *
 * <p><b>三态收敛，NEVER 退回二值判定</b>（2026-09-20 修）：此前这里是
 * {@code isSuccess(payResult) ? PROCESSING : RETRY}，于是「对端明确拒绝」与「网络超时 / 无响应」
 * 落同一个 {@code RETRY} —— 前者重推一万次也不会成功，却和后者一起进补偿队列；失败原因还固定写
 * 「调用pay-sign失败」，**支付宝出行渠道那一支的文案从来是错的**（它压根没调 pay-sign）。
 * 现在按 {@link RpcOutcome} 三分支落库，判据与本模块 {@code MetroTransferPushTaskProcessor} 一致：
 * <ul>
 *   <li>{@code Ok} → {@code PROCESSING}，等支付结果回调收敛终态；</li>
 *   <li>{@code BizRejected} → <b>{@code FAIL} 终态</b>，等人工核对。<b>代价 MUST 知情</b>：
 *       {@code FAIL} 不在 {@link DebitStatus#isRetryable} 白名单里，因此运营后台的重试入口
 *       （{@code GateTxnPayManualOpsService}）**不再能重试这笔**。这是按用户 2026-09-20 的裁决取的，
 *       理由是业务拒绝重推无用；**要改成可重试 MUST 同批动那个白名单，NEVER 只把这里改回 RETRY**
 *       —— 那等于又把两类失败混回一起；</li>
 *   <li>{@code Unreachable} → {@code RETRY}，这才是唯一该进补偿的一类。</li>
 * </ul>
 *
 * <p>三态的翻译收口在 {@link PayInitiationRpcAdapter}，本类只负责「翻完之后落哪个状态、写什么原因」。
 * {@code switch} 写成**表达式**而不是语句：新增 {@code RpcOutcome} 子类型时**编译期直接报错**，
 * 不靠人记得回来补分支。
 */
@Component
public class PaySignInitiator {
    private static final Logger log = LoggerFactory.getLogger(PaySignInitiator.class);

    /** {@code GATE_TXN_PAY.REMARK} 是 {@code VARCHAR2(512 CHAR)}，超长直接 {@code ORA-12899}。 */
    private static final int REMARK_MAX_LENGTH = 512;

    /** 渠道名只进日志与 {@code REMARK}，**NEVER 拿它当分支判据** —— 判据是 {@link IssueChannelCodeEnum#isAlipay}。 */
    private static final String CHANNEL_PAY_SIGN = "pay-sign";
    private static final String CHANNEL_ALIPAY = "alipay-pay-sign";

    private final GateTxnPayWriter gateTxnPayWriter;
    private final PayInitiationRpcAdapter payInitiationRpcAdapter;
    /** 两个渠道的报文组装已各自搬到一个工厂：本类因此从「14 个构造参数」降到 4 个、 且不再读任何 {@code @Value} 配置。 */
    private final GatePayRequestFactory gatePayRequestFactory;
    private final AlipayTripPayRequestFactory alipayTripPayRequestFactory;

    public PaySignInitiator(
            GateTxnPayWriter gateTxnPayWriter,
            PayInitiationRpcAdapter payInitiationRpcAdapter,
            GatePayRequestFactory gatePayRequestFactory,
            AlipayTripPayRequestFactory alipayTripPayRequestFactory) {
        this.gateTxnPayWriter = gateTxnPayWriter;
        this.payInitiationRpcAdapter = payInitiationRpcAdapter;
        this.gatePayRequestFactory = gatePayRequestFactory;
        this.alipayTripPayRequestFactory = alipayTripPayRequestFactory;
    }

    /** 异步入口：出站首次扣款走这里。 */
    public void initiateAsync(GateTxnPay order, GateTxnPayReqDTO request) {
        try {
            initiateAndConverge(order, request);
        } catch (Exception e) {
            log.error("异步扣费编排异常, orderNo={}, cardId={}", order.getOrderNo(), order.getCardId(), e);
            try {
                gateTxnPayWriter.updateOrderStatusFromPending(order, DebitStatus.RETRY.code(),
                        remark("异步扣费编排异常: " + e.getMessage()));
            } catch (RuntimeException ex) {
                log.error("异步更新订单状态失败, orderNo={}", order.getOrderNo(), ex);
                throw ex;
            }
        }
    }

    /** 同步入口（出站首次与离线码补偿共用），返回收敛后的 {@code DEBIT_STATUS}。 */
    public String initiateAndConverge(GateTxnPay order, GateTxnPayReqDTO request) {
        return converge(order, request, "扣费");
    }

    /** 运营重试入口：无入向 request，支付相关字段只能取订单快照。 */
    public String retryAndConverge(GateTxnPay order) {
        return converge(order, null, "重试扣费");
    }

    /**
     * BOM 补站单专用：请支付域补一行「已完成、ITP 实收 0 元」的支付流水，让这笔订单成为完整订单。
     *
     * <p><b>这不是发起扣款，NEVER 合进上面三个入口</b>：BOM 补站（{@code adviceOpt} 005/006/020）的钱由
     * BOM 现场收走，ITP 一分不扣（见 {@code GateTxnPayServiceImpl} 的 {@code BOM_SUPPLEMENT_ADVICE_OPTS} 注释）。
     * 补这行流水只为「有订单必有流水」，因此 {@code amount} <b>恒传 0</b> ——
     * 把 BOM 现场收的钱填进来等于把支付域的退款防线拆了。
     *
     * <p>失败即整笔失败（用户 2026-09-22 选定强一致口径）：调用方 MUST 在落单<b>之前</b>调本方法，
     * 返回非 null 就不要落单。**NEVER 改成「先落单、失败只记日志」** —— 那正是本次要消灭的
     * 「有订单没流水」状态，而且落单成功后再也没有第二个入口会来补这行。
     *
     * @return 成功返 {@code null}；失败返写进日志与响应的原因（{@code BizRejected} 与 {@code Unreachable}
     *         这里<b>同样处置</b>：本链路不落中间态、没有补偿队列，可重试与不可重试都只能让整笔失败退回闸机）
     */
    public String registerCompletedTxnForBomSupplement(GateTxnPay order, GateTxnPayReqDTO request, String reason) {
        RegisterCompletedPayTxnReqDTO payTxnRequest = new RegisterCompletedPayTxnReqDTO();
        payTxnRequest.setOrderNo(order.getOrderNo());
        payTxnRequest.setTxnDate(order.getTxnDate());
        payTxnRequest.setThirdUserId(order.getThirdUserId());
        payTxnRequest.setCardId(order.getCardId());
        payTxnRequest.setCardType(order.getCardType());
        payTxnRequest.setPaymentVendor(order.getPaymentVendor());
        payTxnRequest.setPayUserId(order.getPayUserId());
        payTxnRequest.setAmount(0);
        payTxnRequest.setReason(reason);
        if (request != null) {
            if (StringUtils.hasText(request.getPaymentVendor())) {
                payTxnRequest.setPaymentVendor(request.getPaymentVendor());
            }
            if (StringUtils.hasText(request.getPayUserId())) {
                payTxnRequest.setPayUserId(request.getPayUserId());
            }
            payTxnRequest.setRequestSignSeq(request.getRequestSignSeq());
        }
        RpcOutcome outcome = payInitiationRpcAdapter.registerCompletedTxn(payTxnRequest);
        return switch (outcome) {
            case RpcOutcome.Ok ignored -> {
                log.info("BOM补站单已在支付域登记0元支付流水, orderNo={}, cardId={}, reason={}",
                        order.getOrderNo(), order.getCardId(), reason);
                yield null;
            }
            case RpcOutcome.BizRejected rejected -> {
                log.error("BOM补站单登记支付流水被支付域业务拒绝，本笔不落单, orderNo={}, cardId={}, retCode={}, retMsg={}",
                        order.getOrderNo(), order.getCardId(), rejected.retCode(), rejected.retMsg());
                yield "登记支付流水被拒绝: " + rejected.retCode() + "/" + rejected.retMsg();
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("BOM补站单登记支付流水未获支付域答复，本笔不落单等闸机重推, orderNo={}, cardId={}",
                        order.getOrderNo(), order.getCardId(), unreachable.cause());
                yield "登记支付流水未获答复: " + causeOf(unreachable);
            }
        };
    }

    private String converge(GateTxnPay order, GateTxnPayReqDTO request, String scene) {
        boolean alipayChannel = IssueChannelCodeEnum.isAlipay(order.getIssueChannelCode());
        String channel = alipayChannel ? CHANNEL_ALIPAY : CHANNEL_PAY_SIGN;
        RpcOutcome outcome = alipayChannel ? requestAlipayTripPay(order) : requestPaySign(order, request);
        Landing landing = land(order, scene, channel, outcome);
        gateTxnPayWriter.updateOrderStatusFromPending(order, landing.status(), landing.reason());
        return landing.status();
    }

    /**
     * 把三态翻成「落哪个状态 + 写什么原因」。
     *
     * @param scene 场景前缀（{@code 扣费} / {@code 重试扣费}），只进文案
     * @param channel 实际调用的渠道名，**MUST 进 REMARK** —— 这是上一版最大的坑：两个渠道共用一句
     *                「调用pay-sign失败」，事后翻 {@code REMARK} 分不清到底调的是谁
     */
    private Landing land(GateTxnPay order, String scene, String channel, RpcOutcome outcome) {
        return switch (outcome) {
            case RpcOutcome.Ok ignored -> {
                log.info("{}已被{}受理，置 PROCESSING 等支付结果回调, orderNo={}, cardId={}, amount={}",
                        scene, channel, order.getOrderNo(), order.getCardId(), order.getTotalAmount());
                yield new Landing(DebitStatus.PROCESSING.code(), remark(scene + "已被" + channel + "受理"));
            }
            case RpcOutcome.BizRejected rejected -> {
                log.error("{}被{}业务拒绝，已置 FAIL 终态待人工核对（NEVER 重推）, orderNo={}, cardId={}, retCode={}, retMsg={}",
                        scene, channel, order.getOrderNo(), order.getCardId(), rejected.retCode(), rejected.retMsg());
                yield new Landing(DebitStatus.FAIL.code(),
                        remark(scene + "被" + channel + "业务拒绝: " + rejected.retCode() + "/" + rejected.retMsg()));
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("{}未获{}业务答复，已置 RETRY 等补偿重试, orderNo={}, cardId={}",
                        scene, channel, order.getOrderNo(), order.getCardId(), unreachable.cause());
                yield new Landing(DebitStatus.RETRY.code(),
                        remark(scene + "未获" + channel + "答复: " + causeOf(unreachable)));
            }
        };
    }

    /** 调支付中心通道发起免密扣款。 */
    private RpcOutcome requestPaySign(GateTxnPay order, GateTxnPayReqDTO request) {
        GatePayRequestDTO payRequest = gatePayRequestFactory.build(order, request);
        log.info("调用pay-sign请求支付, 入参 orderNo={}, cardId={}, amount={}, paymentVendor={}, requestSignSeq={}, txnDate={}, discountFee={}, discountInfo={}",
                order.getOrderNo(), order.getCardId(), order.getTotalAmount(),
                payRequest.getPaymentVendor(), payRequest.getRequestSignSeq(),
                payRequest.getTxnDate(), payRequest.getDiscountFee(), payRequest.getDiscountInfo());
        return payInitiationRpcAdapter.requestPaySign(payRequest);
    }

    /** 支付宝出行免密扣费。 */
    private RpcOutcome requestAlipayTripPay(GateTxnPay order) {
        AlipayTripRequestPayReqDTO payRequest = alipayTripPayRequestFactory.build(order);
        log.info("调用alipay-pay-sign请求支付, 入参 orderNo={}, cardId={}, amount={}, requestSignSeq={}, txnDate={}, industryDetail={}",
                order.getOrderNo(), order.getCardId(), order.getTotalAmount(),
                payRequest.getRequestSignSeq(), order.getTxnDate(), payRequest.getIndustryDetail());
        return payInitiationRpcAdapter.requestAlipayTripPay(payRequest);
    }

    private String causeOf(RpcOutcome.Unreachable unreachable) {
        Throwable cause = unreachable.cause();
        if (cause == null) {
            return "未知原因";
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** {@code REMARK} 列长上限裁剪，形态照 {@code MetroTransferPushTaskProcessor.truncate}。 */
    private String remark(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > REMARK_MAX_LENGTH ? text.substring(0, REMARK_MAX_LENGTH) : text;
    }

    /** 一次发起的落库结论：目标 {@code DEBIT_STATUS} 与写进 {@code REMARK} 的原因。 */
    private record Landing(String status, String reason) {
    }
}
