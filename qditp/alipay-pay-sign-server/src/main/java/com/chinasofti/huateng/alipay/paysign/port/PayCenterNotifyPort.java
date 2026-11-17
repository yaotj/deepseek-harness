package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;

import java.util.Map;


public interface PayCenterNotifyPort {

    PayCenterResponse blacklistNotify(Map<String, Object> bizData);

    PayCenterResponse closeResultNotify(Map<String, Object> bizData);
}
