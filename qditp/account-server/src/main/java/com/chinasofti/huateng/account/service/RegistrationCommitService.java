package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;


public interface RegistrationCommitService {

    RegisterRideStatusRespDTO registerRideStatus(UserItpRegInfo regInfo);


    void persistRegistration(UserItpRegInfo regInfo);


    String normalizeIssueOrgCode(String cardIssueCode);
}
