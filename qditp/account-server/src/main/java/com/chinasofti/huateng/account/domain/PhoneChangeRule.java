package com.chinasofti.huateng.account.domain;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import org.springframework.util.StringUtils;


public final class PhoneChangeRule {

    public enum Precondition {

        NO_ACTIVE_USER,

        UNCHANGED,

        PROCEED
    }

    private PhoneChangeRule() {
    }


    public static boolean hasRequiredFields(String thirdUserId, String newMsisdn) {
        return StringUtils.hasText(thirdUserId) && StringUtils.hasText(newMsisdn);
    }


    public static Precondition decide(UserItpRegInfo regInfo, String newMsisdn) {
        if (regInfo == null) {
            return Precondition.NO_ACTIVE_USER;
        }
        String oldMsisdn = regInfo.getMsisdn();
        if (oldMsisdn != null && oldMsisdn.equals(newMsisdn)) {
            return Precondition.UNCHANGED;
        }
        return Precondition.PROCEED;
    }
}
