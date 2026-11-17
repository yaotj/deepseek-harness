package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.domain.EmployeeCardEvent;
import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;


public interface EmployeeCardPersistenceService {

    void saveFromStatusNotify(EmployeeCardInfoDTO source);


    boolean applyActivationResult(String cardNo, int targetStatus, boolean markOpenTms, String remark);


    void refreshProfileFromAcc(UserAccEmployeeCard target, EmployeeCardInfoDTO source);


    void recordEvent(String cardNo, EmployeeCardEvent eventType, Integer cardStatus, String remark);


    void attachEmployeeCardsQuietly(String thirdUserId, String msisdn);
}
