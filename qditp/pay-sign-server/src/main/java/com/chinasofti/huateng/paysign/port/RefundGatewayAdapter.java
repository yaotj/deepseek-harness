package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RefundGatewayAdapter implements RefundGatewayPort {

    private final PaySignProperties paySignProperties;
    private final PaySignGateway paySignGateway;

    public RefundGatewayAdapter(PaySignProperties paySignProperties, PaySignGateway paySignGateway) {
        this.paySignProperties = paySignProperties;
        this.paySignGateway = paySignGateway;
    }

    @Override
    public GatewayReply requestRefund(Map<String, Object> bizData) {
        return judge(paySignGateway.request(paySignProperties.getRequestRefundUrl(), bizData));
    }

    @Override
    public GatewayReply queryRefund(Map<String, Object> bizData) {
        return judge(paySignGateway.request(paySignProperties.getRefundQueryUrl(), bizData));
    }

    @Override
    public boolean refundQueryConfigured() {
        return StringUtils.hasText(paySignProperties.getRefundQueryUrl());
    }


    @Override
    public String refundNotifyUrl() {
        return paySignProperties.getRequestRefundNotifyUrl();
    }

    private GatewayReply judge(PaySignGatewayResponse response) {
        return paySignGateway.isSuccess(response)
                ? new GatewayReply.Accepted(response)
                : new GatewayReply.Rejected(response);
    }
}
