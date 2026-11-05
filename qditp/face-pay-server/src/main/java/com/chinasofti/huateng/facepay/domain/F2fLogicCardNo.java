package com.chinasofti.huateng.facepay.domain;

import java.util.Locale;

public final class F2fLogicCardNo {

    private F2fLogicCardNo() {
    }

    public static String normalize(String logicCardNo) {
        return logicCardNo == null ? null : logicCardNo.toUpperCase(Locale.ROOT);
    }
}
