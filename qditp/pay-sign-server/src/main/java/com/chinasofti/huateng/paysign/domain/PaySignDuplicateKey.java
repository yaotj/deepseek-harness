package com.chinasofti.huateng.paysign.domain;

import org.springframework.dao.DuplicateKeyException;


public final class PaySignDuplicateKey {

    private PaySignDuplicateKey() {
    }


    public static boolean isConflict(Throwable e) {
        for (Throwable cause = e; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
