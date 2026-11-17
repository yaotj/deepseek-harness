package com.chinasofti.huateng.gatetxnpay.paysign;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class PayInitiationRpcAdapter {

    private static final Logger log = LoggerFactory.getLogger(PayInitiationRpcAdapter.class);

    private final PaySignClient paySignClient;
    private final AlipayPaySignClient alipayPaySignClient;

    public PayInitiationRpcAdapter(PaySignClient paySignClient, AlipayPaySignClient alipayPaySignClient) {
        this.paySignClient = paySignClient;
        this.alipayPaySignClient = alipayPaySignClient;
    }

    public RpcOutcome requestPaySign(GatePayRequestDTO payRequest) {
        try {
            RequestPayResult response = paySignClient.requestPay(payRequest);
            if (response == null) {
                log.error("调用pay-sign请求支付：应答为空, orderNo={}", payRequest.getOrderNo());
                return new RpcOutcome.BizRejected(null, "pay-sign 应答为空");
            }
            log.info("调用pay-sign请求支付, 返回={}", JSON.toJSONString(response));
            return RpcOutcome.ofRetCode(response.getRetCode(), response.getRetMsg());
        } catch (Exception e) {
            log.error("调用pay-sign请求支付未获业务答复（可重试）, orderNo={}", payRequest.getOrderNo(), e);
            return new RpcOutcome.Unreachable(e);
        }
    }

    public RpcOutcome requestAlipayTripPay(AlipayTripRequestPayReqDTO payRequest) {
        try {
            AlipayTripRequestPayRespDTO response = alipayPaySignClient.alipayTripRequestPay(payRequest);
            if (response == null) {
                log.error("调用alipay-pay-sign请求支付：应答为空, orderNo={}", payRequest.getOrderNo());
                return new RpcOutcome.BizRejected(null, "alipay-pay-sign 应答为空");
            }
            log.info("调用alipay-pay-sign请求支付, 返回={}", JSON.toJSONString(response));
            return RpcOutcome.ofRetCode(response.getRetCode(), response.getRetMsg());
        } catch (Exception e) {
            log.error("调用alipay-pay-sign请求支付未获业务答复（可重试）, orderNo={}", payRequest.getOrderNo(), e);
            return new RpcOutcome.Unreachable(e);
        }
    }


    public RpcOutcome registerCompletedTxn(RegisterCompletedPayTxnReqDTO request) {
        try {
            RpcOutcome outcome = paySignClient.registerCompletedTxn(request);
            log.info("调用pay-sign登记已完成支付流水, orderNo={}, outcome={}", request.getOrderNo(), outcome);
            return outcome;
        } catch (Exception e) {
            log.error("调用pay-sign登记已完成支付流水未获业务答复（可重试）, orderNo={}", request.getOrderNo(), e);
            return new RpcOutcome.Unreachable(e);
        }
    }
}
