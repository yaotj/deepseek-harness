package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;

/**
 * IF8A-42 用户销户。
 */
public interface AccountCancelService {
    /**
     * IF8A-42 用户销户：把该用户全部有效开户记录置为已注销。
     */
    UserCancelResult userCancel(UserCancelReqDTO request);
}
