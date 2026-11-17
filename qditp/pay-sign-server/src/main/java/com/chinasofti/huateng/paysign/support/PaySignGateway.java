package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaySignGateway {

    private final PayGatewayClient payGatewayClient;

    public PaySignGateway(PayGatewayClient payGatewayClient) {
        this.payGatewayClient = payGatewayClient;
    }

    public PaySignGatewayResponse request(String url, Map<String, Object> bizData) {
        return payGatewayClient.request(url, bizData);
    }

    public boolean isSuccess(PaySignGatewayResponse response) {
        return payGatewayClient.isSuccess(response);
    }

    public String errorMessage(PaySignGatewayResponse response, String defaultMsg) {
        return payGatewayClient.errorMessage(response, defaultMsg);
    }

    public boolean isAlreadyPaidSuccess(PaySignGatewayResponse response) {
        if (response == null) {
            return false;
        }
        Integer code = response.getCode();
        String msg = response.getMsg();
        return Integer.valueOf(9999).equals(code)
                && (msg != null && (msg.contains("已支付成功") || msg.contains("请勿重复支付")));
    }
}
