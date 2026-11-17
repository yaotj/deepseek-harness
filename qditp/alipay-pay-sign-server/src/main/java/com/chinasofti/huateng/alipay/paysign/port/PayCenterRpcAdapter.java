package com.chinasofti.huateng.alipay.paysign.port;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;


@Component
public class PayCenterRpcAdapter implements PayCenterPort {

    @Autowired
    private PayCenterClient payCenterClient;

    @Override
    public PayCenterReply requestPay(Map<String, Object> bizData) {
        return translate(payCenterClient.requestPay(bizData));
    }

    @Override
    public PayCenterReply payQuery(Map<String, Object> bizData) {
        return translate(payCenterClient.payQuery(bizData));
    }

    @Override
    public PayCenterReply requestRefund(Map<String, Object> bizData) {
        return translate(payCenterClient.requestRefund(bizData));
    }

    @Override
    public PayCenterReply refundQuery(Map<String, Object> bizData) {
        return translate(payCenterClient.refundQuery(bizData));
    }

    private PayCenterReply translate(PayCenterResponse response) {
        if (response == null) {
            return new PayCenterReply.NoAnswer();
        }
        String rawBody = JSON.toJSONString(response);
        boolean transportOk = (response.getCode() != null && response.getCode() == 200)
                || Boolean.TRUE.equals(response.getSuccess());
        if (!transportOk) {
            return new PayCenterReply.Rejected(
                    response.getCode(), response.getSuccess(), response.getMsg(), rawBody);
        }
        Map<String, Object> data = payCenterClient.decodeDataMap(response);
        String retCode = firstNonNull(data.get("retCode"), data.get("returnCode"));
        String retMsg = firstNonNull(data.get("retMsg"), data.get("returnMsg"));
        return new PayCenterReply.Accepted(
                response.getCode(), response.getSuccess(), response.getMsg(), rawBody,
                retCode, retMsg, data);
    }

    private String firstNonNull(Object primary, Object fallback) {
        if (primary != null) {
            return primary.toString();
        }
        return fallback == null ? null : fallback.toString();
    }
}
