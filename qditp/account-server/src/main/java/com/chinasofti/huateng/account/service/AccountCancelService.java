package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;


public interface AccountCancelService {

    UserCancelResult userCancel(UserCancelReqDTO request);
}
