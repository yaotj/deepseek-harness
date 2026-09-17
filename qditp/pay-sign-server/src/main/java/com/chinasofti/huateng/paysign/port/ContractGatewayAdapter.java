package com.chinasofti.huateng.paysign.port;

import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildContractBizData;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildCreditQueryBizData;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildDismissalBizData;
import static com.chinasofti.huateng.paysign.support.PaySignGatewayMessages.buildQueryResultBizData;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** {@link ContractGatewayPort} 的唯一实现：**签约/解约方向所有出向 URL 与应答判读的收口点**。 */
@Component
public class ContractGatewayAdapter implements ContractGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(ContractGatewayAdapter.class);

    private final PaySignProperties paySignProperties;
    private final PaySignGateway paySignGateway;

    public ContractGatewayAdapter(PaySignProperties paySignProperties, PaySignGateway paySignGateway) {
        this.paySignProperties = paySignProperties;
        this.paySignGateway = paySignGateway;
    }

    @Override
    public GatewayReply requestContract(RequestSignInfoReqDTO request, String paymentVendor) {
        Map<String, Object> bizData = buildContractBizData(request, paymentVendor, resolveNotifyUrl(request));
        log.info("IF8A-16调用支付平台签约, requestSignSeq={}, thirdUserId={}",
                request.getRequestSignSeq(), request.getThirdUserId());
        PaySignGatewayResponse response = paySignGateway.request(paySignProperties.getContractUrl(), bizData);
        log.info("IF8A-16支付平台返回, requestSignSeq={}, gatewayResponse={}",
                request.getRequestSignSeq(), JSON.toJSONString(response));
        return judge(response);
    }

    @Override
    public GatewayReply creditQuery(String thirdUserId, String requestSignSeq, String paymentVendor) {
        return judge(paySignGateway.request(paySignProperties.getContractAdvisoryUrl(),
                buildCreditQueryBizData(thirdUserId, requestSignSeq, paymentVendor)));
    }

    @Override
    public GatewayReply queryContractResult(String requestSignSeq) {
        return judge(paySignGateway.request(paySignProperties.getContractResultUrl(),
                buildQueryResultBizData(requestSignSeq)));
    }

    @Override
    public GatewayReply requestDismissal(String requestSignSeq) {
        return judge(paySignGateway.request(paySignProperties.getTerminationUrl(),
                buildDismissalBizData(requestSignSeq)));
    }

    /** 成功码判定的**唯一入口**。 */
    private GatewayReply judge(PaySignGatewayResponse response) {
        return paySignGateway.isSuccess(response)
                ? new GatewayReply.Accepted(response)
                : new GatewayReply.Rejected(response);
    }

    /** 签约回调地址：报文 {@code notifyUrl} → 配置默认值 → {@code returnUrl}。 */
    private String resolveNotifyUrl(RequestSignInfoReqDTO request) {
        if (StringUtils.hasText(request.getNotifyUrl())) {
            return request.getNotifyUrl();
        }
        if (StringUtils.hasText(paySignProperties.getDefaultNotifyUrl())) {
            return paySignProperties.getDefaultNotifyUrl();
        }
        return request.getReturnUrl();
    }
}
