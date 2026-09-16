package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPaymentReqDTO;
import com.chinasofti.huateng.facepay.api.paycenter.PayCenterResponses;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterPayCommand;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayScene;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 付款码支付（IF2A-11 {@code requestPayment}）。设备扫乘客付款码后由 ITP 代为发起扣款。
 *
 * <h2>三处与旧实现的差异，都是有意的</h2>
 * <ol>
 *   <li><b>不再轮询。</b>旧 {@code BomOrderServiceImpl.getBomPayResult} 在请求线程上
 *       {@code Thread.sleep} 轮询到 {@code bom.payTimeOut}，违反 AGENTS.md §5.2
 *       「NEVER 在请求线程上做长时间阻塞」。本实现只发一次支付、必要时补一次查询就返回，
 *       剩下的由设备自己调 {@code requestPayResult} 收口。</li>
 *   <li><b>对端没答上来时回 {@code ORDERED} 而不是失败。</b>付款码已经扫过、钱可能已经扣了，
 *       回 FAILED 会让设备当场提示失败并可能重复收款。这与
 *       {@link PaymentResult} 类注释里已定的口径一致。</li>
 *   <li><b>订单从统一的 {@code F2F_ORDER} 查。</b>旧实现这条 URL 挂在 {@code /itptvm} 下却去查
 *       {@code BOM_NO_CASH_ORDER}，TVM 自己下的单在那张表里根本不存在——即 TVM 付款码支付
 *       一直查不到订单。新表合一后这个缺陷自然消失。</li>
 * </ol>
 *
 * <p><b>错误码族是 8999 而不是 2999</b>，见 {@link BomResponses} 类注释，NEVER 统一。</p>
 *
 * <p>整个类不带 {@code @Transactional}：链路里有支付中心调用。</p>
 */
@Service
public class F2fScanPayService {

    private static final Logger log = LoggerFactory.getLogger(F2fScanPayService.class);

    private static final String STATUS_CREATED = F2fOrderStatus.CREATED.name();
    private static final String STATUS_PAYING = F2fOrderStatus.PAYING.name();
    private static final String STATUS_PAY_FAILED = F2fOrderStatus.PAY_FAILED.name();

    /** 对设备口径为「已收款」的内部状态白名单，与 {@link F2fTvmOrderService} 保持一致。 */
    private static final List<String> PAID_LIKE = List.of(
            F2fOrderStatus.PAID.name(), F2fOrderStatus.FULFILLED.name(), F2fOrderStatus.FULFILL_FAILED.name(),
            F2fOrderStatus.REFUNDING.name(), F2fOrderStatus.REFUNDED.name());

    private static final List<String> FAILED_LIKE = F2fOrderStatus.FAILED_LIKE;

    /** 只有这两个状态才允许发起扣款，其余一律短路。白名单，NEVER 改成黑名单。 */
    private static final List<String> PAYABLE = F2fOrderStatus.PENDING;

    private static final String PAY_SUBJECT = "bom支付";

    private static final String PAY_BODY = "地铁单程票";

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final PayCenterMessageFactory messageFactory;

    private final F2fPayCenterFlow payCenterFlow;

    public F2fScanPayService(F2fOrderMapper orderMapper,
                             F2fPaymentMapper paymentMapper,
                             PayCenterMessageFactory messageFactory,
                             F2fPayCenterFlow payCenterFlow) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.messageFactory = messageFactory;
        this.payCenterFlow = payCenterFlow;
    }

    /** 付款码支付。返回体是 BOM 族（{@code 0000} / {@code 8999}）。 */
    public JSONObject requestPayment(RequestPaymentReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("付款码支付 订单不存在, orderNo={}", orderNo);
            return BomResponses.paymentResultFail(BomResponses.CODE_ORDER_NO_ERROR,
                    "订单号错误,没有找到匹配的订单");
        }
        String status = order.getOrderStatus();
        if (PAID_LIKE.contains(status)) {
            log.info("付款码支付 订单已收款，幂等返回成功, orderNo={}, status={}", orderNo, status);
            return BomResponses.paymentResult(PaymentResult.SUCCESS, "支付成功");
        }
        if (FAILED_LIKE.contains(status)) {
            log.info("付款码支付 订单已是失败终态, orderNo={}, status={}", orderNo, status);
            return BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
        }
        if (!PAYABLE.contains(status)) {
            log.warn("付款码支付 命中未预期状态，拒绝扣款, orderNo={}, status={}", orderNo, status);
            return BomResponses.paymentResultFail(BomResponses.CODE_FAIL, "订单状态不允许支付");
        }
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            log.error("付款码支付 订单金额非法, orderNo={}, amount={}", orderNo, order.getOrderAmount());
            return BomResponses.paymentResultFail(BomResponses.CODE_FAIL, "订单金额异常，请联系工作人员");
        }
        return payAtPayCenter(order, request);
    }

    /** <b>必须在事务外</b>：中间那次 {@code execute} 是网络调用。 */
    private JSONObject payAtPayCenter(F2fOrder order, RequestPaymentReqDTO request) {
        String orderNo = order.getOrderNo();
        long amount = order.getOrderAmount();
        LocalDateTime now = LocalDateTime.now();

        PayCenterRequest message;
        try {
            message = messageFactory.buildPayRequest(new PayCenterPayCommand(
                    orderNo, PayScene.SCAN, request.getPaymentCode(), null, amount,
                    PAY_SUBJECT, PAY_BODY, request.getPaymentVendor()));
        } catch (IllegalArgumentException e) {
            log.warn("付款码支付 报文要素不全，按支付失败返回不发往支付中心, orderNo={}, reason={}",
                    orderNo, e.getMessage());
            return BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
        }
        int attemptNo = nextAttemptNo(orderNo);
        paymentMapper.insert(buildPayment(order, request, attemptNo, message.getBizData(), now));

        F2fPayCenterFlow.Submitted submitted = payCenterFlow.submit(new F2fPayCenterFlow.SubmitSpec(
                orderNo, attemptNo, message, null,
                new F2fPayCenterFlow.RejectTransition(PAYABLE, "付款码支付被拒:"),
                true, "付款码支付"));
        return switch (submitted) {
            // 同步就收到钱：先落成功的支付流水，再推订单 PAID。顺序 MUST 保持。
            case F2fPayCenterFlow.Submitted.SyncPaid paid -> {
                markSuccess(orderNo, attemptNo, paid.result());
                payCenterFlow.markPaidAndReport(orderNo);
                log.info("付款码支付成功, orderNo={}", orderNo);
                yield BomResponses.paymentResult(PaymentResult.SUCCESS, "支付成功");
            }
            // 被拒与「返回支付失败」对设备是同一句话，差别只在日志（已在 flow 里分开打）。
            case F2fPayCenterFlow.Submitted.Rejected ignored ->
                    BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
            // NEVER 改成 FAILED：付款码已经扫过、钱可能已扣，回失败会让 BOM 重新收款。
            case F2fPayCenterFlow.Submitted.Unknown ignored ->
                    BomResponses.paymentResult(PaymentResult.ORDERED, "支付中心未返回结果，请稍后查询");
            case F2fPayCenterFlow.Submitted.Accepted ignored ->
                    BomResponses.paymentResult(PaymentResult.ORDERED, "支付处理中，请稍后查询");
        };
    }

    /**
     * IF8A-06 BOM 轮询查询支付结果。返回体是 BOM 族。
     *
     * <p>与 TVM 的 {@code requestPayResult} 是同一件事，只有响应壳不同：TVM 回
     * {@code paymentResult/paymentResultDesc/paymentChannelCode}，BOM 回
     * {@code paymentResult/paymentResultDesc/msg}。业务判定完全共用
     * {@link #resolve}，因此不存在两套状态映射。</p>
     *
     * <p>本地已是终态则短路；只有 {@code CREATED} / {@code PAYING} 才去问支付中心。
     * <b>问不到时回 {@code ORDERED} 让 BOM 继续轮询</b>，NEVER 回 FAILED。</p>
     */
    public JSONObject queryPayResult(String orderNo) {
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("BOM 查询支付结果 订单不存在, orderNo={}", orderNo);
            return BomResponses.paymentResultFail(BomResponses.CODE_ORDER_NO_ERROR,
                    "订单号错误,没有找到匹配的订单");
        }
        String status = order.getOrderStatus();
        if (PAID_LIKE.contains(status)) {
            return BomResponses.paymentResult(PaymentResult.SUCCESS, "支付成功");
        }
        if (FAILED_LIKE.contains(status)) {
            return BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
        }
        if (!PAYABLE.contains(status)) {
            log.warn("BOM 查询支付结果 命中未预期状态，按处理中返回, orderNo={}, status={}", orderNo, status);
            return BomResponses.paymentResult(PaymentResult.PROCESSING, "处理中");
        }
        return resolve(orderNo);
    }

    /** 向支付中心查实际结果并收口本地状态。<b>必须在事务外</b>。 */
    private JSONObject resolve(String orderNo) {
        F2fPayCenterFlow.Settled settled = payCenterFlow.settle(new F2fPayCenterFlow.SettleSpec(
                orderNo, PAYABLE,
                result -> markSuccess(orderNo, lastAttemptNo(orderNo), result),
                "BOM 付款码"));
        return switch (settled.settlement()) {
            case PAID -> BomResponses.paymentResult(PaymentResult.SUCCESS, "支付成功");
            case FAILED -> BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
            case UNPAID -> BomResponses.paymentResult(PaymentResult.FAILED, "未支付");
            // NEVER 回 FAILED：问不到结论时钱可能已经收了，让 BOM 继续轮询。
            case PENDING -> BomResponses.paymentResult(PaymentResult.PROCESSING, "处理中");
        };
    }

    /** 收口时要落在最近一次支付尝试上；查不到按 1 处理。 */
    private int lastAttemptNo(String orderNo) {
        Integer max = paymentMapper.selectMaxAttemptNo(orderNo);
        return max == null ? 1 : max;
    }

    /**
     * {@code markSuccess} 返回 0 或撞唯一键都属正常
     * （回调先到、或并发第二次扣款尝试），一律幂等吞掉。
     * <b>NEVER 让这里的异常打断订单状态推进</b>——钱确实收到了。
     *
     * <p>撞键判定走 {@link F2fDuplicateKey#isConflict(Throwable)} 的 cause 链，
     * <b>NEVER 退回 {@code catch (DuplicateKeyException)}</b>：本模块一旦打开 tracing，
     * 观测切面会把异常包一层、按类型 catch 当场失效（AGENTS.md §5.2）。</p>
     */
    private void markSuccess(String orderNo, int attemptNo, PayCenterResult result) {
        try {
            int updated = paymentMapper.markSuccess(orderNo, attemptNo,
                    result.string("orderNo"), result.string("channelOrderNo"),
                    result.string("paymentVendor"), PayCenterResponses.CODE_SUCCESS,
                    "付款码支付成功", LocalDateTime.now());
            if (updated == 0) {
                log.info("付款码支付 该尝试已是终态，无需再置成功, orderNo={}, attemptNo={}", orderNo, attemptNo);
            }
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("该订单已有成功的支付尝试，幂等跳过, orderNo={}", orderNo);
        }
    }

    /** 付款码支付可以重试（第一次码过期、第二次换码），因此 attemptNo 递增而不是固定 1。 */
    private int nextAttemptNo(String orderNo) {
        Integer max = paymentMapper.selectMaxAttemptNo(orderNo);
        return max == null ? 1 : max + 1;
    }

    /**
     * <b>设备侧这两个字段的命名与实际语义是反的，落库与外发都 MUST 按语义放、NEVER 按名字放。</b>
     *
     * <p>设备上送的 {@code paymentCode} 其实是<b>支付渠道码</b>（实测 {@code 03}），
     * {@code paymentVendor} 其实是<b>用户付款码</b>（实测 18 位数字）。旧实现在
     * {@code BomOrderServiceImpl:272} → {@code PayCenterCommon:96,103} 完成这次交叉：
     * 设备 {@code paymentCode} → 网关 {@code paymentVendor}，
     * 设备 {@code paymentVendor} → 网关 {@code authCode}。</p>
     *
     * <p>2026-09-10 BOM 售票重放实测：本方法原先照名字直接对应，把 18 位付款码写进
     * {@code PAYMENT_VENDOR VARCHAR2(8)}，当场 {@code ORA-12899}、响应退化成 UUID retCode；
     * 即便列宽够，外发报文也会把付款码当渠道码、把 {@code 03} 当付款码，支付中心必然拒付。</p>
     */
    private F2fPayment buildPayment(F2fOrder order, RequestPaymentReqDTO request, int attemptNo,
                                    String requestBody, LocalDateTime now) {
        F2fPayment payment = new F2fPayment();
        payment.setOrderNo(order.getOrderNo());
        payment.setAttemptNo(attemptNo);
        payment.setPayScene(PayScene.SCAN.getCode());
        payment.setPayStatus("INIT");
        payment.setPayAmount(order.getOrderAmount());
        payment.setPaymentVendor(request.getPaymentCode());
        payment.setAuthCode(request.getPaymentVendor());
        payment.setRequestBody(requestBody);
        payment.setRequestTms(now);
        payment.setCreateTms(now);
        payment.setUpdateTms(now);
        return payment;
    }
}
