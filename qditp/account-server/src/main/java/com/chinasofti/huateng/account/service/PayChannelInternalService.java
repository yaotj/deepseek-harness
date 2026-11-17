package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractReqDTO;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractResult;
import com.chinasofti.huateng.model.app.SyncPayAccountIdReqDTO;


public interface PayChannelInternalService {

    QueryPayChannelByContractResult queryPayChannelByContractNo(QueryPayChannelByContractReqDTO request);


    CommonResult syncPayAccountId(SyncPayAccountIdReqDTO request);
}
