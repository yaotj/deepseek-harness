package com.chinasofti.huateng.facepay.domain;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLIntegrityConstraintViolationException;

public final class F2fDuplicateKey {

    private F2fDuplicateKey() {
    }

    public static boolean isConflict(Throwable throwable) {
        for (Throwable cursor = throwable; cursor != null; ) {
            if (cursor instanceof DuplicateKeyException
                    || cursor instanceof DataIntegrityViolationException
                    || cursor instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            cursor = cursor.getCause() == cursor ? null : cursor.getCause();
        }
        return false;
    }
}
