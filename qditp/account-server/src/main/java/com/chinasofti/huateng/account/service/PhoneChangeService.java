package com.chinasofti.huateng.account.service;


public interface PhoneChangeService {

    boolean updatePhone(String thirdUserId, String newMsisdn);


    SignSyncCompensateResult compensateSignSync();


    record SignSyncCompensateResult(int scanned, int success, int failed) {
    }
}
