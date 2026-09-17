package com.chinasofti.huateng.gatetxnpay.paysign;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 出站扣费的唯一出账口：组报文调 pay-sign，并把结果收敛成 `DEBIT_STATUS`。 */
@Component
public class PaySignInitiator {
    private static final Logger log = LoggerFactory.getLogger(PaySignInitiator.class);

    private final PaySignClient paySignClient;
    private final AlipayPaySignClient alipayPaySignClient;
    private final GateTxnPayWriter gateTxnPayWriter;
    /** 两个渠道的报文组装已各自搬到一个工厂：本类因此从「14 个构造参数」降到 5 个、 且不再读任何 {@code @Value} 配置。 */
    private final GatePayRequestFactory gatePayRequestFactory;
    private final AlipayTripPayRequestFactory alipayTripPayRequestFactory;

    public PaySignInitiator(
            PaySignClient paySignClient,
            AlipayPaySignClient alipayPaySignClient,
            GateTxnPayWriter gateTxnPayWriter,
            GatePayRequestFactory gatePayRequestFactory,
            AlipayTripPayRequestFactory alipayTripPayRequestFactory) {
        this.paySignClient = paySignClient;
        this.alipayPaySignClient = alipayPaySignClient;
        this.gateTxnPayWriter = gateTxnPayWriter;
        this.gatePayRequestFactory = gatePayRequestFactory;
        this.alipayTripPayRequestFactory = alipayTripPayRequestFactory;
    }

    /** 异步入口：出站首次扣款走这里。 */
    public void initiateAsync(GateTxnPay order, GateTxnPayReqDTO request) {
        try {
            initiateAndConverge(order, request);
        } catch (Exception e) {
            log.error("异步调用pay-sign异常, orderNo={}", order.getOrderNo(), e);
            try {
                gateTxnPayWriter.updateOrderStatusFromPending(order, DebitStatus.RETRY.code(),
                        "异步调用pay-sign异常: " + e.getMessage());
            } catch (RuntimeException ex) {
                log.error("异步更新订单状态失败, orderNo={}", order.getOrderNo(), ex);
                throw ex;
            }
        }
    }

    /** 同步入口（出站首次与离线码补偿共用），返回收敛后的 {@code DEBIT_STATUS}。 */
    public String initiateAndConverge(GateTxnPay order, GateTxnPayReqDTO request) {
        return converge(order, request, "调用pay-sign失败");
    }

    /** 运营重试入口：无入向 request，支付相关字段只能取订单快照。 */
    public String retryAndConverge(GateTxnPay order) {
        return converge(order, null, "重试调用pay-sign失败");
    }

    private String converge(GateTxnPay order, GateTxnPayReqDTO request, String noResponseReason) {
        RequestPayResult payResult = requestPay(order, request);
        String nextStatus = isSuccess(payResult) ? DebitStatus.PROCESSING.code() : DebitStatus.RETRY.code();
        String reason = payResult == null ? noResponseReason : payResult.getRetMsg();
        gateTxnPayWriter.updateOrderStatusFromPending(order, nextStatus, reason);
        return nextStatus;
    }

    /** 渠道分派：{@code ISSUE_CHANNEL_CODE=07} 走支付宝出行，其余走支付中心（pay-sign）。 */
    private RequestPayResult requestPay(GateTxnPay order, GateTxnPayReqDTO request) {
        if (IssueChannelCodeEnum.isAlipay(order.getIssueChannelCode())) {
            return requestAlipayTripPay(order);
        }
        return requestPaySign(order, request);
    }

    /** 调用 pay-sign 发起免密扣款。 */
    private RequestPayResult requestPaySign(GateTxnPay order, GateTxnPayReqDTO request) {
        GatePayRequestDTO payRequest = gatePayRequestFactory.build(order, request);
        log.info("调用pay-sign请求支付, 入参 orderNo={}, cardId={}, amount={}, paymentVendor={}, requestSignSeq={}, txnDate={}, discountFee={}, discountInfo={}",
                order.getOrderNo(), order.getCardId(), order.getTotalAmount(),
                payRequest.getPaymentVendor(), payRequest.getRequestSignSeq(),
                payRequest.getTxnDate(), payRequest.getDiscountFee(), payRequest.getDiscountInfo());
        RequestPayResult response = paySignClient.requestPay(payRequest);
        log.info("调用pay-sign请求支付, 返回={}", JSON.toJSONString(response));
        return response;
    }

    /** 支付宝出行免密扣费。 */
    private RequestPayResult requestAlipayTripPay(GateTxnPay order) {
        AlipayTripRequestPayReqDTO payRequest = alipayTripPayRequestFactory.build(order);
        log.info("调用alipay-pay-sign请求支付, 入参 orderNo={}, cardId={}, amount={}, requestSignSeq={}, txnDate={}, industryDetail={}",
                order.getOrderNo(), order.getCardId(), order.getTotalAmount(),
                payRequest.getRequestSignSeq(), order.getTxnDate(), payRequest.getIndustryDetail());
        AlipayTripRequestPayRespDTO response = alipayPaySignClient.alipayTripRequestPay(payRequest);
        log.info("调用alipay-pay-sign请求支付, 返回={}", JSON.toJSONString(response));
        if (response == null) {
            return null;
        }
        RequestPayResult result = new RequestPayResult();
        result.setRetCode(response.getRetCode());
        result.setRetMsg(response.getRetMsg());
        return result;
    }

    private boolean isSuccess(RequestPayResult result) {
        return result != null && GateTxnPayRetCode.SUCCESS.equals(result.getRetCode());
    }
}
