package com.chinasofti.huateng.alipay.paysign.domain;

import org.springframework.util.StringUtils;


public final class PayCenterTradeStatus {

    public static final String SUCCESS = "SUCCESS";

    public static final String FAIL = "FAIL";

    private PayCenterTradeStatus() {
    }


    public static String normalize(String tradeStatus) {
        if (!StringUtils.hasText(tradeStatus)) {
            return null;
        }
        String normalized = tradeStatus.trim().toUpperCase();
        if (SUCCESS.equals(normalized) || "PAID".equals(normalized)) {
            return SUCCESS;
        }
        if (FAIL.equals(normalized) || "FAILED".equals(normalized)) {
            return FAIL;
        }
        return null;
    }


    public static boolean isConfirmedFail(String tradeStatus) {
        return FAIL.equals(normalize(tradeStatus));
    }
}
