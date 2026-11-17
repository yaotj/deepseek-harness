package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;

import java.util.List;
import java.util.Map;


public interface EmployeeCardOutboundService {

    record AppRegisterResult(Map<String, String> failReasons) {

        public String failureReasonOf(String cardNo) {
            return failReasons.get(cardNo);
        }
    }


    AppRegisterResult registerToApp(List<EmployeeCardInfoDTO> batch);


    EmployeeCardInfoDTO queryFromAcc(String cardNo);


    boolean isActivationUrlConfigured();


    String requestActivation(EmployeeCardActivateReqDTO request);
}
