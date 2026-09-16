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

/**
 * {@link ContractGatewayPort} 的唯一实现：**签约/解约方向所有出向 URL 与应答判读的收口点**
 * （2026-09-16，ADR-D112；形态照 {@link AccountDomainRpcAdapter}）。
 *
 * <p><b>本类是「哪个 URL」这件事在本方向的唯一持有者</b>。NEVER 让领域服务再注
 * {@code PaySignProperties} 只为取 URL —— 那正是收口前的形态。
 *
 * <p>报文装配继续复用 {@code PaySignGatewayMessages} 的静态纯函数（ADR-D98），
 * 本类只做「选 URL + 调 + 判读」这三件事，<b>NEVER 往里加业务判断</b>
 * （状态机、金额、渠道分派一律留在领域服务）。
 */
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

    /**
     * 成功码判定的**唯一入口**。
     *
     * <p>沿用 {@code PaySignGateway.isSuccess}（内部即 {@code PayGatewayClient} 的
     * {@code code=0 或 SUCCESS_CODE}），<b>NEVER 在本类重写一份判定</b>。
     */
    private GatewayReply judge(PaySignGatewayResponse response) {
        return paySignGateway.isSuccess(response)
                ? new GatewayReply.Accepted(response)
                : new GatewayReply.Rejected(response);
    }

    /**
     * 签约回调地址：报文 {@code notifyUrl} → 配置默认值 → {@code returnUrl}。
     *
     * <p>由 {@code ContractDomainServiceImpl.resolveNotifyUrl} 原样搬入（ADR-D112）。
     * <b>顺序 NEVER 调整</b>：三级回落是对外契约的一部分，改了会把回调打到另一个地址。
     */
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
