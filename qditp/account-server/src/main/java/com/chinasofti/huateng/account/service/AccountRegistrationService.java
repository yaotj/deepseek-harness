package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;


public interface AccountRegistrationService {

    RequestApplicationResult requestApplication(RequestApplicationReqDTO request);
}
