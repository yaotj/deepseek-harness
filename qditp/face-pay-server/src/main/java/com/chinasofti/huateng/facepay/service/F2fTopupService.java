package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fLogicCardNo;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestTopupReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterPayCommand;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** TVM 票卡充值下单：IF2A-09。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fTopupService {

    private static final Logger log = LoggerFactory.getLogger(F2fTopupService.class);

    /** 业务类型：充值，对应 {@code CK_F2F_ORDER_BIZ} 的 02。 */
    private static final String BIZ_TOPUP = "02";

    private static final String STATUS_CREATED = F2fOrderStatus.CREATED.name();

    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    private static final String PAY_SUBJECT = "地铁票卡充值";

    private static final String PAY_BODY = "地铁票卡充值";

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fOrderNoGenerator orderNoGenerator;

    private final PayCenterMessageFactory messageFactory;

    private final F2fPayCenterFlow payCenterFlow;

    private final int qrcodeExpireSeconds;

    public F2fTopupService(F2fOrderMapper orderMapper,
                          F2fPaymentMapper paymentMapper,
                          F2fOrderNoGenerator orderNoGenerator,
                          PayCenterMessageFactory messageFactory,
                          F2fPayCenterFlow payCenterFlow,
                          @Value("${f2f.order.qrcodeExpireSeconds:180}") int qrcodeExpireSeconds) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.messageFactory = messageFactory;
        this.payCenterFlow = payCenterFlow;
        this.qrcodeExpireSeconds = qrcodeExpireSeconds;
    }
    /** IF2A-09 充值下单。 */
    public JSONObject requestTopup(RequestTopupReqDTO request) {
        Long transAmount = request.transAmountInFen();
        Long beforeAmount = request.beforeAmountInFen();
        if (transAmount == null || beforeAmount == null) {
            log.warn("充值下单入参非数字, transAmount={}, beforeAmount={}",
                    request.getTransAmount(), request.getBeforeAmount());
            return TvmResponses.topupFail(DeviceRetCode.INVALID_PARAM, "transAmount或beforeAmount不是合法数字");
        }
        if (transAmount <= 0) {
            return TvmResponses.topupFail(DeviceRetCode.INVALID_PARAM, "transAmount必须为正数");
        }

        String logicNum = F2fLogicCardNo.normalize(request.getTicketLogicNum());
        String orderNo = orderNoGenerator.next(F2fOrderNo.BIZ_TOPUP);
        LocalDateTime now = LocalDateTime.now();
        orderMapper.insert(buildOrder(orderNo, request, logicNum, transAmount, beforeAmount, now));
        log.info("充值下单已落库, orderNo={}, transAmount={}, ticketLogicNum={}",
                orderNo, transAmount, logicNum);

        if ("0".equals(request.getPayType())) {
            String payUrl = messageFactory.buildAggregateCodePayUrl(orderNo);
            paymentMapper.insert(buildPayment(orderNo, transAmount, request.getPayType(),
                    "INIT", payUrl, null, now));
            log.info("充值 payType=0 走本地聚合码, orderNo={}", orderNo);
            return TvmResponses.topupSuccess(orderNo, payUrl);
        }
        return preOrderAtPayCenter(orderNo, transAmount, request.getPayType(), now);
    }

    /** 必须在事务外：中间那次 {@code execute} 是网络调用。 */
    private JSONObject preOrderAtPayCenter(String orderNo, long amount, String payType, LocalDateTime now) {
        PayCenterRequest message = messageFactory.buildPayRequest(new PayCenterPayCommand(
                orderNo, PayScene.QRCODE, null, payType, amount, PAY_SUBJECT, PAY_BODY, null));
        paymentMapper.insert(buildPayment(orderNo, amount, payType, "INIT", null, message.getBizData(), now));

        F2fPayCenterFlow.Submitted submitted = payCenterFlow.submit(new F2fPayCenterFlow.SubmitSpec(
                orderNo, 1, message, "二维码串",
                new F2fPayCenterFlow.RejectTransition(PENDING, "充值预下单失败:"),
                false, "充值预下单"));
        return switch (submitted) {
            case F2fPayCenterFlow.Submitted.Accepted accepted ->
                    TvmResponses.topupSuccess(orderNo, accepted.result().string("data"));
            case F2fPayCenterFlow.Submitted.Rejected ignored -> TvmResponses.topupFail(DeviceRetCode.FAIL);
            case F2fPayCenterFlow.Submitted.Unknown ignored -> TvmResponses.topupFail(DeviceRetCode.FAIL);
            case F2fPayCenterFlow.Submitted.SyncPaid ignored ->
                    throw new IllegalStateException("充值拉码不应收到同步支付成功, orderNo=" + orderNo);
        };
    }

    private F2fOrder buildOrder(String orderNo, RequestTopupReqDTO request, String logicNum,
                                long transAmount, long beforeAmount, LocalDateTime now) {
        F2fOrder order = new F2fOrder();
        order.setOrderNo(orderNo);
        order.setChannel(F2fChannel.TVM);
        order.setBizType(BIZ_TOPUP);
        order.setOrderStatus(STATUS_CREATED);
        order.setOrderAmount(transAmount);
        order.setDeviceId(request.getDeviceId());
        order.setCardId(logicNum);
        order.setTicketPhysicsNum(request.getTicketPhysicsNum());
        order.setCardBeforeAmount(beforeAmount);
        order.setActivateFlag("0");
        order.setExpireTms(now.plusSeconds(qrcodeExpireSeconds));
        order.setCreateTms(now);
        order.setUpdateTms(now);
        return order;
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
