package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import org.springframework.stereotype.Component;

import java.util.Map;


@Component
public class PayCenterNotifyRpcAdapter implements PayCenterNotifyPort {

    private final PayCenterClient payCenterClient;

    public PayCenterNotifyRpcAdapter(PayCenterClient payCenterClient) {
        this.payCenterClient = payCenterClient;
    }

    @Override
    public PayCenterResponse blacklistNotify(Map<String, Object> bizData) {
        return payCenterClient.blacklistNotify(bizData);
    }

    @Override
    public PayCenterResponse closeResultNotify(Map<String, Object> bizData) {
        return payCenterClient.closeResultNotify(bizData);
    }
}
