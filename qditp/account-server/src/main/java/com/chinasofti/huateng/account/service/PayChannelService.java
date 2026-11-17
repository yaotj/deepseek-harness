package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;


public interface PayChannelService {

    RequestAddPayChannelResult requestAddPayChannel(RequestAddPayChannelReqDTO request);


    RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(RequestSetDefaultPayChannelReqDTO request);


    RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(RequestUpdateChannelDefaultContractReqDTO request);


    RequestRemovePayChannelResult requestRemovePayChannel(RequestRemovePayChannelReqDTO request);


    RequestRemovePayChannelResult requestAgreeRelease(RequestRemovePayChannelReqDTO request);
}
