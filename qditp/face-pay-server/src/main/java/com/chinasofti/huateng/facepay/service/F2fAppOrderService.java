package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.app.AppResponses;
import com.chinasofti.huateng.facepay.api.device.app.RequestAppPayResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.RequestOrderReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.RequestQueryActiveOrderReqDTO;
import com.chinasofti.huateng.facepay.api.paycenter.PayCenterResponses;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterPayCommand;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.channel.paycenter.PayScene;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import com.chinasofti.huateng.facepay.support.F2fOrderNo;
import com.chinasofti.huateng.facepay.support.F2fOrderNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** APP 扫码取票：下单、请求支付信息、支付结果查询、激活订单列表。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fAppOrderService {

    private static final Logger log = LoggerFactory.getLogger(F2fAppOrderService.class);

    /** 业务类型：取票，对应 {@code CK_F2F_ORDER_BIZ} 的 03。 */
    private static final String BIZ_TAKE_TICKET = "03";

    /** 交易类型：APP 扫码取票。 */
    private static final String TRANS_TYPE_APP_TAKE_TICKET = "03";

    private static final String STATUS_CREATED = F2fOrderStatus.CREATED.name();
    private static final String STATUS_PAYING = F2fOrderStatus.PAYING.name();
    private static final String STATUS_PAID = F2fOrderStatus.PAID.name();
    private static final String STATUS_PAY_FAILED = F2fOrderStatus.PAY_FAILED.name();
    private static final String STATUS_FULFILLED = F2fOrderStatus.FULFILLED.name();
    private static final String STATUS_FULFILL_FAILED = F2fOrderStatus.FULFILL_FAILED.name();
    private static final String STATUS_REFUNDING = F2fOrderStatus.REFUNDING.name();

    private static final List<String> PAID_LIKE = List.of(
            STATUS_PAID, STATUS_FULFILLED, STATUS_FULFILL_FAILED, STATUS_REFUNDING, F2fOrderStatus.REFUNDED.name());

    private static final List<String> FAILED_LIKE = F2fOrderStatus.FAILED_LIKE;

    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    /** {@code ACTIVATE_FLAG} 已激活。 */
    private static final String ACTIVATED = "1";

    private static final String PAY_SUBJECT = "APP单程票购票";

    private static final String PAY_BODY = "地铁单程票";

    private static final DateTimeFormatter TMS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** IF8A-18 本地已终态时回吐的支付时间格式。 */
    private static final DateTimeFormatter PAID_TMS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** IF8A-18 本地终态分支的 {@code payDate}：有值按 {@code yyyy-MM-dd HH:mm:ss} 输出， 无值返回 null 而不是空串——旧实现原样吐 {@code PAY_TIME} 列值，失败单该列多为 null。 */
    private static String paidTimeOf(F2fOrder order) {
        return order.getPaidTms() == null ? null : order.getPaidTms().format(PAID_TMS_FORMATTER);
    }

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fOrderNoGenerator orderNoGenerator;

    private final PayCenterMessageFactory messageFactory;

    private final F2fPayCenterFlow payCenterFlow;

    public F2fAppOrderService(F2fOrderMapper orderMapper,
                             F2fPaymentMapper paymentMapper,
                             F2fOrderNoGenerator orderNoGenerator,
                             PayCenterMessageFactory messageFactory,
                             F2fPayCenterFlow payCenterFlow) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.messageFactory = messageFactory;
        this.payCenterFlow = payCenterFlow;
    }
    /** IF8A-20 下单。 */
    public JSONObject createOrder(RequestOrderReqDTO request) {
        Long price = request.priceInFen();
        Integer count = request.ticketCount();
        if (price == null || count == null) {
            log.warn("APP 下单入参非数字, ticketPrice={}, singelTicketNum={}",
                    request.getTicketPrice(), request.getSingelTicketNum());
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM,
                    "ticketPrice或singelTicketNum不是合法数字");
        }
        if (price <= 0 || count <= 0) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM,
                    "ticketPrice与singelTicketNum必须为正数");
        }
        String orderNo = orderNoGenerator.next(F2fOrderNo.BIZ_SINGLE_TICKET);
        LocalDateTime now = LocalDateTime.now();
        orderMapper.insert(buildOrder(orderNo, request, price, count, now));
        log.info("APP 取票下单已落库, orderNo={}, userId={}, amount={}",
                orderNo, request.getUserId(), price * count);
        return AppResponses.orderNo(orderNo);
    }

    /** IF8A-11 请求支付信息。 */
    public JSONObject requestPayInfo(RequestPayInfoReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("APP 请求支付信息 订单不存在, orderNo={}", orderNo);
            return AppResponses.failMessage("订单号错误");
        }
        if (!STATUS_CREATED.equals(order.getOrderStatus())) {
            log.info("APP 请求支付信息 订单状态异常, orderNo={}, status={}", orderNo, order.getOrderStatus());
            return AppResponses.failMessage("订单状态异常");
        }
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            log.error("APP 请求支付信息 订单金额非法, orderNo={}, amount={}", orderNo, order.getOrderAmount());
            return AppResponses.failMessage("订单金额异常，请联系工作人员");
        }

        LocalDateTime now = LocalDateTime.now();
        PayCenterRequest message = messageFactory.buildPayRequest(new PayCenterPayCommand(
                orderNo, PayScene.APP, request.getPayChannelCode(), request.getPayChannelCode(),
                order.getOrderAmount(), PAY_SUBJECT, PAY_BODY, null));
        int attemptNo = nextAttemptNo(orderNo);
        paymentMapper.insert(buildPayment(order, request.getPayChannelCode(), attemptNo,
                message.getBizData(), now));

        F2fPayCenterFlow.Submitted submitted = payCenterFlow.submit(new F2fPayCenterFlow.SubmitSpec(
                orderNo, attemptNo, message, "支付信息", null, false, "APP 请求支付信息"));
        return switch (submitted) {
            case F2fPayCenterFlow.Submitted.Accepted accepted -> AppResponses.payInfo(
                    request.getPayChannelCode(), accepted.result().string("data"));
            case F2fPayCenterFlow.Submitted.Rejected rejected ->
                    AppResponses.failMessage("获取支付信息失败[" + rejected.result().getCode() + "]");
            case F2fPayCenterFlow.Submitted.Unknown unknown -> unknown.result().isTransportFailed()
                    ? AppResponses.failMessage("支付中心暂时不可用，请稍后重试")
                    : AppResponses.failMessage("获取支付信息失败");
            case F2fPayCenterFlow.Submitted.SyncPaid ignored ->
                    throw new IllegalStateException("APP 请求支付信息不应收到同步支付成功, orderNo=" + orderNo);
        };
    }
    /** IF8A-18 支付结果查询。 */
    public JSONObject queryPayResult(RequestAppPayResultReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("APP 查询支付结果 订单不存在, orderNo={}", orderNo);
            return AppResponses.failMessage("订单号错误");
        }
        String status = order.getOrderStatus();
        if (PAID_LIKE.contains(status)) {
            return AppResponses.payResult(payCenterOrderNoOf(orderNo), "SUCCESS",
                    order.getOrderAmount(), paidTimeOf(order));
        }
        if (FAILED_LIKE.contains(status)) {
            return AppResponses.payResult(payCenterOrderNoOf(orderNo), "FAIL",
                    order.getOrderAmount(), paidTimeOf(order));
        }
        if (!PENDING.contains(status)) {
            log.warn("APP 查询支付结果 命中未预期状态，按支付中返回, orderNo={}, status={}", orderNo, status);
            return AppResponses.fail(AppResponses.CODE_PAYING, "支付中");
        }
        return resolvePayResult(order);
    }

    /** 向支付中心查实际结果并收口本地状态。 */
    private JSONObject resolvePayResult(F2fOrder order) {
        String orderNo = order.getOrderNo();
        F2fPayCenterFlow.Settled settled = payCenterFlow.settle(new F2fPayCenterFlow.SettleSpec(
                orderNo, PENDING, result -> markPaymentSuccess(orderNo, result), "APP"));
        PayCenterResult result = settled.result();
        return switch (settled.settlement()) {
            case PAID -> AppResponses.payResult(result.string("channelOrderNo"), "SUCCESS",
                    order.getOrderAmount(), LocalDateTime.now().format(TMS_FORMATTER));
            case FAILED, UNPAID -> AppResponses.payResult(result.string("channelOrderNo"), "FAIL",
                    order.getOrderAmount(), "");
            case PENDING -> AppResponses.fail(AppResponses.CODE_PAYING, "支付中");
        };
    }

    /** 「已收款但订单没推到 PAID」的 ERROR 上报已收口到 {@link F2fPayCenterFlow#markPaidAndReport(String)}，本类不再自留副本。 */

    /** 获取已激活的取票订单列表。 */
    public JSONObject listActiveOrders(RequestQueryActiveOrderReqDTO request) {
        if (!request.isQdMetro()) {
            log.info("获取激活订单 appType 不支持, appType={}", request.getAppType());
            return AppResponses.failMessage("appType有误，请输入正确的值");
        }
        List<F2fOrder> orders = orderMapper.selectByUserAndActivateFlag(request.getUserId(), ACTIVATED);
        JSONArray list = new JSONArray();
        for (F2fOrder order : orders) {
            JSONObject item = new JSONObject();
            item.put("orderNo", order.getOrderNo());
            item.put("entryStationCode", order.getEntryStationCode());
            item.put("exitStationCode", order.getExitStationCode());
            item.put("ticketPrice", order.getTicketPrice() == null
                    ? null : String.valueOf(order.getTicketPrice()));
            item.put("singelTicketNum", order.getTicketNum() == null
                    ? null : String.valueOf(order.getTicketNum()));
            item.put("singleTicketType", order.getSingleTicketType());
            item.put("orderDate", format(order.getCreateTms()));
            item.put("payDate", format(order.getPaidTms()));
            list.add(item);
        }
        log.info("获取激活订单完成, userId={}, size={}", request.getUserId(), list.size());
        return AppResponses.orderList(list);
    }
    /** 据支付中心查询结果把最近一次支付尝试收口为 SUCCESS。 */
    private void markPaymentSuccess(String orderNo, PayCenterResult result) {
        Integer attemptNo = paymentMapper.selectMaxAttemptNo(orderNo);
        try {
            int updated = paymentMapper.markSuccess(orderNo, attemptNo == null ? 1 : attemptNo,
                    result.string("orderNo"), result.string("channelOrderNo"),
                    result.string("paymentVendor"), PayCenterResponses.CODE_SUCCESS,
                    "查询到支付成功", LocalDateTime.now());
            if (updated == 0) {
                log.info("APP 支付尝试已是终态，无需再置成功, orderNo={}", orderNo);
            }
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("该订单已有成功的支付尝试，幂等跳过, orderNo={}", orderNo);
        }
    }

    /** 换支付信息可以重试（换支付渠道），因此 attemptNo 递增而不是固定 1。 */
    private int nextAttemptNo(String orderNo) {
        Integer max = paymentMapper.selectMaxAttemptNo(orderNo);
        return max == null ? 1 : max + 1;
    }

    /** 支付中心侧订单号，供 {@code tradeNo} 回吐与退款报文使用；未支付时为 null。 */
    private String payCenterOrderNoOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayCenterOrderNo();
    }

    private F2fOrder buildOrder(String orderNo, RequestOrderReqDTO request,
                                long price, int count, LocalDateTime now) {
        F2fOrder order = new F2fOrder();
        order.setOrderNo(orderNo);
        order.setChannel(F2fChannel.APP);
        order.setBizType(BIZ_TAKE_TICKET);
        order.setTransType(TRANS_TYPE_APP_TAKE_TICKET);
        order.setOrderStatus(STATUS_CREATED);
        order.setOrderAmount(price * count);
        order.setThirdUserId(request.getUserId());
        order.setTicketNum(count);
        order.setTicketPrice(price);
        order.setSingleTicketType(request.getSingleTicketType());
        order.setEntryStationCode(request.getEntryStationCode());
        order.setExitStationCode(request.getExitStationCode());
        order.setActivateFlag("0");
        order.setCreateTms(now);
        order.setUpdateTms(now);
        return order;
    }

    private F2fPayment buildPayment(F2fOrder order, String payChannelCode, int attemptNo,
                                    String requestBody, LocalDateTime now) {
        F2fPayment payment = new F2fPayment();
        payment.setOrderNo(order.getOrderNo());
        payment.setAttemptNo(attemptNo);
        payment.setPayScene(PayScene.APP.getCode());
        payment.setPayStatus("INIT");
        payment.setPayAmount(order.getOrderAmount());
        payment.setPaymentVendor(payChannelCode);
        payment.setRequestBody(requestBody);
        payment.setRequestTms(now);
        payment.setCreateTms(now);
        payment.setUpdateTms(now);
        return payment;
    }

    /** {@code yyyyMMddHHmmss}，null 进空串出。 */
    private static String format(LocalDateTime tms) {
        return tms == null ? "" : tms.format(TMS_FORMATTER);
    }
}
