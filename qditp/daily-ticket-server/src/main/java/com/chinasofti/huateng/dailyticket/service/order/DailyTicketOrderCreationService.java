package com.chinasofti.huateng.dailyticket.service.order;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.payment.DailyTicketPaymentService;
import com.chinasofti.huateng.dailyticket.service.refund.CanceledOrderRefundService;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketPaidFields;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DailyTicketOrderCreationService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketOrderCreationService.class);

    private static final String ORDER_TYPE_DAILY_TICKET = DailyTicketOrderSupport.ORDER_TYPE_DAILY_TICKET;
    private static final String ORDER_TYPE_TRAVEL_TICKET = DailyTicketOrderSupport.ORDER_TYPE_TRAVEL_TICKET;
    private static final String SEA_BUS_PAY_CHANNEL = "SEA_BUS";

    /**
     * 旅游票单次购买张数上限。旅游票下单按张数循环 INSERT，不设上限等于把 for 循环次数交给外部输入。
     * 上限值待业务确认，暂按 20 张。
     */
    private static final int MAX_TRAVEL_TICKET_COUNT = 20;

    private static final String ORDER_STATUS_CANCELED = "CANCELED";
    private static final String ORDER_STATUS_PAYING = "PAYING";
    private static final String ORDER_STATUS_PAID = "PAID";
    private static final String PAY_STATUS_PAYING = "PAYING";
    private static final String PAY_STATUS_PAID = "PAID";

    private final AtomicInteger orderSequence = new AtomicInteger(1);
    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketPaymentService paymentService;
    private final CanceledOrderRefundService canceledOrderRefundService;

    public DailyTicketOrderCreationService(DailyTicketOrderMapper orderMapper,
                                            TravelTicketOrderMapper travelOrderMapper,
                                            DailyTicketInstanceMapper instanceMapper,
                                            DailyTicketPaymentService paymentService,
                                            CanceledOrderRefundService canceledOrderRefundService) {
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.instanceMapper = instanceMapper;
        this.paymentService = paymentService;
        this.canceledOrderRefundService = canceledOrderRefundService;
    }

    /** IF8A-70 旅游票下单。 */
    public TravelTicketOrderResult requestTravelOrder(TravelTicketOrderReqDTO request) {
        TravelTicketOrderResult result = new TravelTicketOrderResult();
        String validMsg = validateTravelOrderRequest(request);
        if (validMsg != null) {
            log.warn("IF8A-70 旅游票下单参数校验失败, msg={}, request={}", validMsg, request);
            return fail(result, validMsg);
        }

        Date now = new Date();
        int ticketPrice = request.getTotalAmount() / request.getTicketCount();
        int ticketCount = request.getTicketCount();
        String travelOrderNo = nextTravelOrderNo();
        boolean seaBusOrder = isSeaBusOrderSource(request.getOrderSource());

        List<String> subOrderNos = new ArrayList<>(ticketCount);
        for (int i = 0; i < ticketCount; i++) {
            DailyTicketOrder sub = buildTravelSubOrder(request, travelOrderNo, ticketPrice, now);
            if (seaBusOrder) {
                sub.setPayChannelCode(SEA_BUS_PAY_CHANNEL);
            }
            orderMapper.insert(sub);
            subOrderNos.add(sub.getOrderNo());
        }

        TravelTicketOrder main = buildTravelMainOrder(request, travelOrderNo, ticketPrice, ticketCount, now);
        if (seaBusOrder) {
            markSeaBusTravelOrderPaid(main, now);
        }
        travelOrderMapper.insert(main);

        log.info("IF8A-70 旅游票下单完成, orderNo={}, ticketCount={}, totalAmount={}",
                travelOrderNo, ticketCount, request.getTotalAmount());
        success(result);
        result.setOrderNo(travelOrderNo);
        result.setSubOrders(JSON.toJSONString(subOrderNos));
        return result;
    }

    public DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request) {
        DailyTicketOrderResult result = new DailyTicketOrderResult();
        String validMsg = validateOrderRequest(request);
        if (validMsg != null) {
            return fail(result, validMsg);
        }

        Date now = new Date();
        DailyTicketOrder order = new DailyTicketOrder();
        order.setId(nextId());
        order.setOrderNo(nextOrderNo());
        order.setOrderType(ORDER_TYPE_DAILY_TICKET);
        order.setOrderSource(request.getOrderSource());
        order.setTicketPrice(request.getTicketPrice());
        order.setCardType(request.getCardType());
        order.setShowType(request.getShowType());
        order.setUserId(request.getUserId());
        if (isSeaBusOrderSource(request.getOrderSource())) {
            markSeaBusDailyOrderPaid(order, now);
        } else {
            order.setOrderStatus("CREATED");
            order.setPayStatus("INIT");
        }
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        success(result);
        result.setOrderNo(order.getOrderNo());
        return result;
    }

    /** IF8A-73 免费票请求下单。 */
    public DailyTicketFreeOrderResult requestOrderFree(DailyTicketFreeOrderReqDTO request) {
        DailyTicketFreeOrderResult result = new DailyTicketFreeOrderResult();
        String validMsg = validateFreeOrderRequest(request);
        if (validMsg != null) {
            log.warn("IF8A-73 免费票请求下单参数校验失败, msg={}, request={}", validMsg, request);
            return fail(result, validMsg);
        }
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return requestFreeTravelOrder(request, result);
        }
        return requestFreeDailyOrder(request, result);
    }

    /**
     * IF8A-65 取消订单：日票与旅游票在**未激活**之前都可取消。
     *
     * <p>前置白名单（**MUST 用白名单，NEVER 写成「非终态即可取消」**）：
     * {@code CREATED} / {@code PAYING} / {@code PAY_FAILED} / {@code PAID}；
     * {@code CANCELED} 幂等返成功、{@code REFUNDING} / {@code REFUNDED} 一律拒。
     * 「已激活」的判据是 {@code DAILY_TICKET_INSTANCE} 有同 {@code ORDER_NO} 的行（旅游票是任一子单有行），
     * 与退款侧 {@code refundType} 的判据同源，**NEVER 换成看订单上的字段** —— 订单表没有激活标记。
     *
     * <p>三条易踩的口径：
     * <ul>
     *   <li>{@code PAYING} 先 {@code queryAndRefreshPayResult} 主动查一次再决策 —— 钱可能已经到账，
     *       直接按未付取消会漏掉退款；</li>
     *   <li>取消已支付单后**当场发起退款**（{@code CanceledOrderRefundService}），订单随退款链路走
     *       {@code CANCELED → REFUNDING → REFUNDED}；</li>
     *   <li>海之巴士（{@code ORDER_SOURCE=4}）单下单即已收款且不走我方支付网关，**一律拒绝取消**，
     *       退款只能由该渠道自己发起。小程序单（{@code ORDER_SOURCE=6}）允许取消，
     *       退款落库后等对方同步结果，与手工退款同一口径。</li>
     * </ul>
     *
     * <p>免费票（{@code FREE-} 前缀）允许取消，退款服务内部会识别并跳过出网 —— 无款可退。
     */
    public DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return cancelTravelOrder(request.getOrderNo(), result);
        }
        return cancelDailyOrder(request.getOrderNo(), result);
    }

    private DailyTicketBaseResult cancelDailyOrder(String orderNo, DailyTicketBaseResult result) {
        DailyTicketOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if (isSeaBusOrderSource(order.getOrderSource())) {
            return fail(result, "订单来源不支持ITP取消");
        }
        if (ORDER_STATUS_CANCELED.equals(order.getOrderStatus())) {
            return success(result);
        }
        if (instanceMapper.selectByOrderNo(orderNo) != null) {
            return fail(result, "车票已激活，不允许取消");
        }
        if (ORDER_STATUS_PAYING.equals(order.getOrderStatus()) || PAY_STATUS_PAYING.equals(order.getPayStatus())) {
            paymentService.queryAndRefreshPayResult(order);
            order = orderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                return fail(result, "订单不存在");
            }
        }
        if (ORDER_STATUS_PAID.equals(order.getOrderStatus()) && PAY_STATUS_PAID.equals(order.getPayStatus())) {
            if (orderMapper.cancelIfPaid(orderNo) == 0) {
                return fail(result, "订单状态已变更，不允许取消");
            }
            log.info("IF8A-65 已支付日票订单取消完成，转自动退款 orderNo={}", orderNo);
            canceledOrderRefundService.refundCanceledDailyOrder(orderNo);
            return success(result);
        }
        if (orderMapper.cancelIfPending(orderNo) == 0) {
            return fail(result, "订单状态不允许取消");
        }
        log.info("IF8A-65 未支付日票订单取消完成 orderNo={}, orderStatusBefore={}", orderNo, order.getOrderStatus());
        return success(result);
    }

    /**
     * 旅游票取消：主单 {@code 0T} 与其下全部未激活子单 {@code 0E} 一并置 {@code CANCELED}。
     *
     * <p>子单取消 **MUST 在主单 CAS 成功之后**做 —— 反过来写时主单 CAS 失败（状态已被别的请求改掉）
     * 会留下「主单还在、子单全没了」的半截数据。幂等分支也再扫一次子单，补上上一次可能漏掉的。
     */
    private DailyTicketBaseResult cancelTravelOrder(String parentOrderNo, DailyTicketBaseResult result) {
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null) {
            return fail(result, "旅游票主订单不存在");
        }
        if (isSeaBusOrderSource(parent.getOrderSource())) {
            return fail(result, "订单来源不支持ITP取消");
        }
        if (ORDER_STATUS_CANCELED.equals(parent.getOrderStatus())) {
            orderMapper.cancelSubOrdersByParent(parentOrderNo);
            return success(result);
        }
        if (hasActivatedSubTicket(parentOrderNo)) {
            return fail(result, "旅游票存在已激活车票，不允许取消");
        }
        if (ORDER_STATUS_PAYING.equals(parent.getOrderStatus()) || PAY_STATUS_PAYING.equals(parent.getPayStatus())) {
            paymentService.queryAndRefreshTravelPayResult(parent);
            parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
            if (parent == null) {
                return fail(result, "旅游票主订单不存在");
            }
        }
        if (ORDER_STATUS_PAID.equals(parent.getOrderStatus()) && PAY_STATUS_PAID.equals(parent.getPayStatus())) {
            if (travelOrderMapper.cancelIfPaid(parentOrderNo) == 0) {
                return fail(result, "旅游票主订单状态已变更，不允许取消");
            }
            int canceledSubs = orderMapper.cancelSubOrdersByParent(parentOrderNo);
            log.info("IF8A-65 已支付旅游票取消完成，转自动整单退款 parentOrderNo={}, canceledSubOrders={}",
                    parentOrderNo, canceledSubs);
            canceledOrderRefundService.refundCanceledTravelOrder(parentOrderNo);
            return success(result);
        }
        if (travelOrderMapper.cancelIfPending(parentOrderNo) == 0) {
            return fail(result, "旅游票主订单状态不允许取消");
        }
        int canceledSubs = orderMapper.cancelSubOrdersByParent(parentOrderNo);
        log.info("IF8A-65 未支付旅游票取消完成 parentOrderNo={}, canceledSubOrders={}", parentOrderNo, canceledSubs);
        return success(result);
    }

    /** 旅游票是否已有任一子单激活；无子单时按「未激活」处理，子单缺失由退款侧再判。 */
    private boolean hasActivatedSubTicket(String parentOrderNo) {
        List<DailyTicketOrder> children = orderMapper.selectByParentOrderNo(parentOrderNo);
        if (children == null) {
            return false;
        }
        for (DailyTicketOrder child : children) {
            if (instanceMapper.selectByOrderNo(child.getOrderNo()) != null) {
                return true;
            }
        }
        return false;
    }

    private DailyTicketFreeOrderResult requestFreeDailyOrder(DailyTicketFreeOrderReqDTO request,
                                                             DailyTicketFreeOrderResult result) {
        Date now = new Date();
        String orderNo = nextOrderNo();
        DailyTicketOrder order = new DailyTicketOrder();
        order.setId(nextId());
        order.setOrderNo(orderNo);
        order.setOrderType(ORDER_TYPE_DAILY_TICKET);
        order.setOrderSource(request.getOrderSource());
        order.setTicketPrice(request.getTicketPrice());
        order.setCardType(request.getCardType());
        order.setShowType(request.getShowType());
        order.setUserId(request.getUserId());
        DailyTicketPaidFields.applyPaid(order, buildFreeTradeNo(orderNo), buildFreeTradeNo(orderNo), 0, now);
        order.setPayChannelCode(request.getPayChannelCode());
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        log.info("IF8A-73 免费日票下单完成, orderNo={}, ticketPrice={}, payChannelCode={}",
                orderNo, request.getTicketPrice(), request.getPayChannelCode());
        success(result);
        result.setOrderNo(orderNo);
        return result;
    }

    private DailyTicketFreeOrderResult requestFreeTravelOrder(DailyTicketFreeOrderReqDTO request,
                                                              DailyTicketFreeOrderResult result) {
        Date now = new Date();
        String travelOrderNo = nextTravelOrderNo();
        int ticketPrice = request.getTicketPrice() == null ? 0 : request.getTicketPrice();

        DailyTicketOrder child = new DailyTicketOrder();
        child.setId(nextId());
        child.setOrderNo(nextOrderNo());
        child.setOrderType(ORDER_TYPE_DAILY_TICKET);
        child.setParentOrderNo(travelOrderNo);
        child.setOrderSource(request.getOrderSource());
        child.setTicketPrice(ticketPrice);
        child.setCardType(request.getCardType());
        child.setShowType(request.getShowType());
        child.setUserId(request.getUserId());
        child.setPayChannelCode(request.getPayChannelCode());
        child.setOrderStatus("CREATED");
        child.setPayStatus("INIT");
        child.setCreateTime(now);
        child.setUpdateTime(now);
        orderMapper.insert(child);

        TravelTicketOrder main = new TravelTicketOrder();
        main.setId(nextId());
        main.setOrderNo(travelOrderNo);
        main.setUserId(request.getUserId());
        main.setCardType(request.getCardType());
        main.setShowType(request.getShowType());
        main.setTicketPrice(ticketPrice);
        main.setTicketCount(1);
        main.setTotalAmount(ticketPrice);
        main.setOrderSource(request.getOrderSource());
        DailyTicketPaidFields.applyPaid(main, buildFreeTradeNo(travelOrderNo),
                buildFreeTradeNo(travelOrderNo), 0, now);
        main.setPayChannelCode(request.getPayChannelCode());
        main.setCreateTime(now);
        main.setUpdateTime(now);
        travelOrderMapper.insert(main);

        log.info("IF8A-73 免费旅游票下单完成, orderNo={}, subOrderNo={}, ticketPrice={}, payChannelCode={}",
                travelOrderNo, child.getOrderNo(), ticketPrice, request.getPayChannelCode());
        success(result);
        result.setOrderNo(travelOrderNo);
        result.setSubOrders(JSON.toJSONString(List.of(child.getOrderNo())));
        return result;
    }

    private void markSeaBusDailyOrderPaid(DailyTicketOrder order, Date now) {
        DailyTicketPaidFields.applyPaid(order, buildSeaBusTradeNo(order.getOrderNo()),
                buildSeaBusTradeNo(order.getOrderNo()), order.getTicketPrice(), now);
        order.setPayChannelCode(SEA_BUS_PAY_CHANNEL);
    }

    private void markSeaBusTravelOrderPaid(TravelTicketOrder order, Date now) {
        DailyTicketPaidFields.applyPaid(order, buildSeaBusTradeNo(order.getOrderNo()),
                buildSeaBusTradeNo(order.getOrderNo()), order.getTotalAmount(), now);
        order.setPayChannelCode(SEA_BUS_PAY_CHANNEL);
    }

    private String nextOrderNo() {
        return "0E" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%04d", orderSequence.getAndIncrement() % 10000);
    }

    /** 旅游票主单号，前缀 0T 与日票子单的 0E 区分，便于日志与运营侧一眼分辨聚合单。 */
    private String nextTravelOrderNo() {
        return "0T" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%04d", orderSequence.getAndIncrement() % 10000);
    }

    private TravelTicketOrder buildTravelMainOrder(TravelTicketOrderReqDTO request, String travelOrderNo,
                                                   int ticketPrice, int ticketCount, Date now) {
        TravelTicketOrder main = new TravelTicketOrder();
        main.setId(nextId());
        main.setOrderNo(travelOrderNo);
        main.setUserId(request.getUserId());
        main.setCardType(request.getCardType());
        main.setShowType(request.getShowType());
        main.setTicketPrice(ticketPrice);
        main.setTicketCount(ticketCount);
        main.setTotalAmount(request.getTotalAmount());
        main.setOrderSource(request.getOrderSource());
        main.setOrderStatus("CREATED");
        main.setPayStatus("INIT");
        main.setCreateTime(now);
        main.setUpdateTime(now);
        return main;
    }

    private DailyTicketOrder buildTravelSubOrder(TravelTicketOrderReqDTO request, String travelOrderNo,
                                                 int ticketPrice, Date now) {
        DailyTicketOrder sub = new DailyTicketOrder();
        sub.setId(nextId());
        sub.setOrderNo(nextOrderNo());
        sub.setOrderType(ORDER_TYPE_DAILY_TICKET);
        sub.setParentOrderNo(travelOrderNo);
        sub.setOrderSource(request.getOrderSource());
        sub.setTicketPrice(ticketPrice);
        sub.setCardType(request.getCardType());
        sub.setShowType(request.getShowType());
        sub.setUserId(request.getUserId());
        sub.setOrderStatus("CREATED");
        sub.setPayStatus("INIT");
        sub.setCreateTime(now);
        sub.setUpdateTime(now);
        return sub;
    }

    private String validateTravelOrderRequest(TravelTicketOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(request.getUserId())) {
            return "userId不能为空";
        }
        if (request.getTicketCount() == null || request.getTicketCount() <= 0) {
            return "ticketCount必须大于0";
        }
        if (request.getTicketCount() > MAX_TRAVEL_TICKET_COUNT) {
            return "ticketCount不能超过" + MAX_TRAVEL_TICKET_COUNT;
        }
        if (request.getTotalAmount() == null || request.getTotalAmount() <= 0) {
            return "totalAmount必须大于0";
        }
        if (request.getTotalAmount() % request.getTicketCount() != 0) {
            return "totalAmount必须能被ticketCount整除";
        }
        return null;
    }

    private String validateOrderRequest(DailyTicketOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(request.getUserId())) {
            return "userId不能为空";
        }
        return null;
    }

    private String validateFreeOrderRequest(DailyTicketFreeOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!ORDER_TYPE_DAILY_TICKET.equals(request.getOrderType())
                && !ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return "orderType必须为1或2";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(request.getUserId())) {
            return "userId不能为空";
        }
        if (request.getTicketPrice() == null || request.getTicketPrice() < 0) {
            return "ticketPrice不能小于0";
        }
        if (!StringUtils.hasText(request.getPayChannelCode())) {
            return "payChannelCode不能为空";
        }
        if (isSeaBusOrderSource(request.getOrderSource())) {
            return "海上巴士订单不支持免费下单";
        }
        return null;
    }

    private String nextId() {
        return DailyTicketOrderSupport.nextId();
    }

    private String validateOrderNo(String orderNo, String orderType) {
        return DailyTicketOrderSupport.validateOrderNo(orderNo, orderType);
    }

    private String buildFreeTradeNo(String orderNo) {
        return DailyTicketOrderSupport.buildFreeTradeNo(orderNo);
    }

    private String buildSeaBusTradeNo(String orderNo) {
        return DailyTicketOrderSupport.buildSeaBusTradeNo(orderNo);
    }

    private boolean isSeaBusOrderSource(String orderSource) {
        return DailyTicketOrderSupport.isSeaBusOrderSource(orderSource);
    }

    private <T extends DailyTicketBaseResult> T success(T result) {
        return DailyTicketOrderSupport.success(result);
    }

    private <T extends DailyTicketBaseResult> T fail(T result, String message) {
        return DailyTicketOrderSupport.fail(result, message);
    }
}
