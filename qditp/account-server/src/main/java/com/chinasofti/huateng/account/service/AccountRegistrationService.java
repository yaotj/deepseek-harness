package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;

/**
 * 开户发号：<b>IF8A-01 APP 渠道开户</b>，只有这一个入口。
 */
public interface AccountRegistrationService {
    /**
     * IF8A-01 请求开户。
     *
     * @param request 请求开户参数
     * @return 请求开户结果
     */
    RequestApplicationResult requestApplication(RequestApplicationReqDTO request);
}
