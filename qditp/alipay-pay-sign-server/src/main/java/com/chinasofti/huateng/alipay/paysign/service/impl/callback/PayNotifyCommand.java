package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import org.springframework.util.StringUtils;


sealed interface PayNotifyCommand {

    String TRANS_STATUS_SUCCESS = "1";
    String TRANS_STATUS_FAIL = "2";


    record Accepted(String orderNo,
                    String rawTransStatus,
                    String payStatus,
                    String channelVoucherId,
                    String transAmount,
                    String transTime) implements PayNotifyCommand {
    }

    record MissingOrderNo() implements PayNotifyCommand {
    }

    record IllegalTransStatus(String orderNo, String rawTransStatus) implements PayNotifyCommand {
    }

    static PayNotifyCommand from(AlipayTripPayNotifyReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return new MissingOrderNo();
        }
        String payStatus = mapTransStatus(request.getTransStatus());
        if (payStatus == null) {
            return new IllegalTransStatus(request.getOrderNo(), request.getTransStatus());
        }
        return new Accepted(request.getOrderNo(), request.getTransStatus(), payStatus,
                request.getChannelVoucherId(), request.getTransAmount(), request.getTransTime());
    }

    private static String mapTransStatus(String transStatus) {
        if (TRANS_STATUS_SUCCESS.equals(transStatus)) {
            return "SUCCESS";
        }
        if (TRANS_STATUS_FAIL.equals(transStatus)) {
            return "FAIL";
        }
        return null;
    }
}
