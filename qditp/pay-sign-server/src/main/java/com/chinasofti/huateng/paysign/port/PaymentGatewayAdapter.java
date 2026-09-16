package com.chinasofti.huateng.paysign.port;

import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildRequestPayBizData;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@link PaymentGatewayPort} 的唯一实现：扣款方向出向 URL 与应答判读的收口点
 * （2026-09-16，ADR-D113 续；形态同 {@link ContractGatewayAdapter}）。
 */
@Component
public class PaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayAdapter.class);

    private final PaySignProperties paySignProperties;
    private final PaySignGateway paySignGateway;

    public PaymentGatewayAdapter(PaySignProperties paySignProperties, PaySignGateway paySignGateway) {
        this.paySignProperties = paySignProperties;
        this.paySignGateway = paySignGateway;
    }

    @Override
    public PaymentReply requestPay(RequestPayReqDTO request) {
        Map<String, Object> bizData = buildRequestPayBizData(request, resolvePayNotifyUrl(request));
        log.info("REQUEST_PAY 调用支付平台, orderNo={}, thirdUserId={}, paymentVendor={}, amount={},"
                        + " discountFee={}, discountInfo={}, bizData={}",
                request.getOrderNo(), request.getThirdUserId(), request.getPaymentVendor(), request.getAmount(),
                request.getDiscountFee(), request.getDiscountInfo(), JSON.toJSONString(bizData));
        PaySignGatewayResponse response = paySignGateway.request(paySignProperties.getRequestPayUrl(), bizData);
        log.info("REQUEST_PAY 支付平台返回, orderNo={}, gatewayResponse={}",
                request.getOrderNo(), JSON.toJSONString(response));
        if (paySignGateway.isSuccess(response)) {
            return new PaymentReply.Accepted(response);
        }
        // 判定顺序 MUST 是「先看成功码、再看幂等措辞」：isAlreadyPaidSuccess 只在非成功码下才有意义。
        if (paySignGateway.isAlreadyPaidSuccess(response)) {
            return new PaymentReply.AlreadyPaid(response);
        }
        return new PaymentReply.Rejected(response);
    }

    @Override
    public GatewayReply queryPayStatus(String merchantOrderNo) {
        if (!StringUtils.hasText(paySignProperties.getPayQueryUrl())) {
            log.error("未配置 pay.sign.pay-query-url，无法确认支付中心真实状态，按不拉黑处理, orderNo={}",
                    merchantOrderNo);
            return new GatewayReply.Rejected(null);
        }
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("merchantOrderNo", merchantOrderNo);
        PaySignGatewayResponse response = paySignGateway.request(paySignProperties.getPayQueryUrl(), bizData);
        log.info("拉黑前查询支付中心支付状态, orderNo={}, queryResponse={}", merchantOrderNo, JSON.toJSONString(response));
        return paySignGateway.isSuccess(response)
                ? new GatewayReply.Accepted(response)
                : new GatewayReply.Rejected(response);
    }

    /**
     * 支付回调地址：报文透传值优先，其次支付专用配置。
     *
     * <p>由 {@code PaymentDomainServiceImpl.resolvePayNotifyUrl} 原样搬入。
     * <b>注意它只有两级，与签约方向的三级回落不同，NEVER 互相看齐</b>。
     */
    private String resolvePayNotifyUrl(RequestPayReqDTO request) {
        if (StringUtils.hasText(request.getNotifyUrl())) {
            return request.getNotifyUrl();
        }
        return paySignProperties.getRequestPayNotifyUrl();
    }
}
