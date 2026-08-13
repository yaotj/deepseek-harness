package com.chinasofti.huateng.fep.app.service.impl;

import com.chinasofti.huateng.fep.app.service.AccountAppService;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.key.KeyClient;
import org.springframework.stereotype.Service;

@Service
public class AccountAppServiceImpl implements AccountAppService {
    private final AccountClient accountClient;
    private final KeyClient keyClient;

    public AccountAppServiceImpl(AccountClient accountClient, KeyClient keyClient) {
        this.accountClient = accountClient;
        this.keyClient = keyClient;
    }

    @Override
    public RequestApplicationResult requestApplication(RequestApplicationReqDTO request) {
        return accountClient.requestApplication(request);
    }

    @Override
    public RequestKeyListResult requestKeyList(RequestKeyListReqDTO request) {
        return keyClient.requestKeyList(request);
    }

    @Override
    public RequestAddPayChannelResult requestAddPayChannel(RequestAddPayChannelReqDTO request) {
        return accountClient.requestAddPayChannel(request);
    }

    @Override
    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(RequestSetDefaultPayChannelReqDTO request) {
        return accountClient.requestSetDefaultPayChannel(request);
    }

    @Override
    public RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(RequestUpdateChannelDefaultContractReqDTO request) {
        return accountClient.requestUpdateChannelDefaultContract(request);
    }

    @Override
    public EmployeeCardQueryResult queryEmployeeCard(EmployeeCardQueryReqDTO request) {
        return accountClient.queryEmployeeCard(request);
    }
}
