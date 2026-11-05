package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestGenSjtOrderReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterPayCommand;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayScene;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.support.F2fOrderNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class F2fTvmOrderService {

    private static final Logger log = LoggerFactory.getLogger(F2fTvmOrderService.class);

    private static final String CHANNEL_TVM = "02";

    private static final String CHANNEL_BOM = "03";

    private static final String BIZ_BUY_TICKET = "01";

    private static final String STATUS_CREATED = F2fOrderStatus.CREATED.name();

    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    private static final String PAY_SUBJECT = "单程票购票";

    private static final String PAY_BODY = "地铁单程票";

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fOrderNoGenerator orderNoGenerator;

    private final PayCenterMessageFactory messageFactory;

    private final F2fPayCenterFlow payCenterFlow;

    private final int qrcodeExpireSeconds;

    private final int bomSaleExpireSeconds;

    public F2fTvmOrderService(F2fOrderMapper orderMapper,
                              F2fPaymentMapper paymentMapper,
                              F2fOrderNoGenerator orderNoGenerator,
                              PayCenterMessageFactory messageFactory,
                              F2fPayCenterFlow payCenterFlow,
                              @Value("${f2f.order.qrcodeExpireSeconds:180}") int qrcodeExpireSeconds,
                              @Value("${f2f.order.bomSaleExpireSeconds:1800}") int bomSaleExpireSeconds) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.messageFactory = messageFactory;
        this.payCenterFlow = payCenterFlow;
        this.qrcodeExpireSeconds = qrcodeExpireSeconds;
        this.bomSaleExpireSeconds = bomSaleExpireSeconds;
    }

    public JSONObject createSingleTicketOrder(RequestGenSjtOrderReqDTO request) {
        TicketAmount parsed = parseTicketAmount(request);
        log.info("拉码下单 parsed is {}", parsed);
        if (parsed.invalid() != null) {
            return TvmResponses.genSjtOrderFail(DeviceRetCode.INVALID_PARAM, parsed.invalid());
        }
        long amount = parsed.total();

        String orderNo = orderNoGenerator.nextSingleTicketOrderNo();
        LocalDateTime now = LocalDateTime.now();
        orderMapper.insert(buildOrder(orderNo, request, CHANNEL_TVM, parsed,
                now.plusSeconds(qrcodeExpireSeconds), now));
        log.info("拉码下单已落库, orderNo={}, amount={}, deviceId={}", orderNo, amount, request.getDeviceId());

        if ("0".equals(request.getPayType())) {
            log.info("payType=0 走本地聚合码, orderNo={}", orderNo);
            String payUrl = messageFactory.buildAggregateCodePayUrl(orderNo);
            paymentMapper.insert(buildPayment(orderNo, amount, request.getPayType(), "INIT", payUrl, null, now));
            return TvmResponses.genSjtOrderSuccess(orderNo, payUrl);
        }
        log.info("开始从支付中心拉码");
        return preOrderAtPayCenter(orderNo, amount, request.getPayType(), now);
    }

    public JSONObject createBomSaleOrder(RequestGenSjtOrderReqDTO request) {
        TicketAmount parsed = parseTicketAmount(request);
        if (parsed.invalid() != null) {
            return TvmResponses.genSjtOrderFail(DeviceRetCode.INVALID_PARAM, parsed.invalid());
        }
        String orderNo = orderNoGenerator.nextSingleTicketOrderNo();
        LocalDateTime now = LocalDateTime.now();
        orderMapper.insert(buildOrder(orderNo, request, CHANNEL_BOM, parsed,
                now.plusSeconds(bomSaleExpireSeconds), now));
        log.info("BOM 售票下单已落库, orderNo={}, amount={}, deviceId={}, entry={}, exit={}",
                orderNo, parsed.total(), request.getDeviceId(),
                request.getEntryStationCode(), request.getExitStationCode());
        return BomResponses.successOrderNo(orderNo);
    }

    private record TicketAmount(long ticketPrice, int ticketNum, long total, String invalid) {
    }

    private TicketAmount parseTicketAmount(RequestGenSjtOrderReqDTO request) {
        long ticketPrice;
        int ticketNum;
        try {
            ticketPrice = Long.parseLong(request.getTicketPrice().trim());
            ticketNum = Integer.parseInt(request.getSingelTicketNum().trim());
        } catch (RuntimeException e) {
            log.warn("下单入参非数字, ticketPrice={}, singelTicketNum={}",
                    request.getTicketPrice(), request.getSingelTicketNum());
            return new TicketAmount(0L, 0, 0L, "ticketPrice或singelTicketNum不是合法数字");
        }
        if (ticketPrice <= 0 || ticketNum <= 0) {
            return new TicketAmount(0L, 0, 0L, "ticketPrice与singelTicketNum必须为正数");
        }
        return new TicketAmount(ticketPrice, ticketNum, ticketPrice * ticketNum, null);
    }

    private F2fOrder buildOrder(String orderNo, RequestGenSjtOrderReqDTO request, String channel,
                                TicketAmount parsed, LocalDateTime expireTms, LocalDateTime now) {
        F2fOrder order = new F2fOrder();
        order.setOrderNo(orderNo);
        order.setChannel(channel);
        order.setBizType(BIZ_BUY_TICKET);
        order.setOrderStatus(STATUS_CREATED);
        order.setOrderAmount(parsed.total());
        order.setDeviceId(request.getDeviceId());
        order.setTicketNum(parsed.ticketNum());
        order.setTicketPrice(parsed.ticketPrice());
        order.setSingleTicketType(request.getSingleTicketType());
        order.setEntryStationCode(request.getEntryStationCode());
        order.setExitStationCode(request.getExitStationCode());
        order.setActivateFlag("0");
        order.setExpireTms(expireTms);
        order.setCreateTms(now);
        order.setUpdateTms(now);
        return order;
    }

    private JSONObject preOrderAtPayCenter(String orderNo, long amount, String payType, LocalDateTime now) {
        PayCenterRequest message = messageFactory.buildPayRequest(new PayCenterPayCommand(
                orderNo, PayScene.QRCODE, null, payType, amount, PAY_SUBJECT, PAY_BODY, null));
        paymentMapper.insert(buildPayment(orderNo, amount, payType, "INIT", null, message.getBizData(), now));
        log.info("payment 表保存结束");
        F2fPayCenterFlow.Submitted submitted = payCenterFlow.submit(new F2fPayCenterFlow.SubmitSpec(
                orderNo, 1, message, "二维码串",
                new F2fPayCenterFlow.RejectTransition(PENDING, "支付中心预下单失败:"),
                false, "拉码下单"));
        return switch (submitted) {
            case F2fPayCenterFlow.Submitted.Accepted accepted ->
                    TvmResponses.genSjtOrderSuccess(orderNo, accepted.result().string("data"));
            case F2fPayCenterFlow.Submitted.Rejected ignored -> TvmResponses.genSjtOrderFail(DeviceRetCode.FAIL);
            case F2fPayCenterFlow.Submitted.Unknown ignored -> TvmResponses.genSjtOrderFail(DeviceRetCode.FAIL);
            case F2fPayCenterFlow.Submitted.SyncPaid ignored ->
                    throw new IllegalStateException("拉码链路不应收到同步支付成功, orderNo=" + orderNo);
        };
    }

    private F2fPayment buildPayment(String orderNo, long amount, String payType, String payStatus,
                                    String payUrl, String requestBody, LocalDateTime now) {
        F2fPayment payment = new F2fPayment();
        payment.setOrderNo(orderNo);
        payment.setAttemptNo(1);
        payment.setPayScene(PayScene.QRCODE.getCode());
        payment.setPayStatus(payStatus);
        payment.setPayAmount(amount);
        payment.setPayType(payType);
        payment.setPayUrl(payUrl);
        payment.setRequestBody(requestBody);
        payment.setRequestTms(now);
        payment.setCreateTms(now);
        payment.setUpdateTms(now);
        return payment;
    }

}
