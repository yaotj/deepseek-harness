package com.chinasofti.huateng.fep.app.service;

import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO;
import com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractResult;
import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryResult;
import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.common.response.CommonResult;

public interface AccountAppService {
    RequestApplicationResult requestApplication(RequestApplicationReqDTO request);

    RequestKeyListResult requestKeyList(RequestKeyListReqDTO request);

    RequestAddPayChannelResult requestAddPayChannel(RequestAddPayChannelReqDTO request);

    RequestRemovePayChannelResult requestAgreeRelease(RequestRemovePayChannelReqDTO request);

    RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(RequestSetDefaultPayChannelReqDTO request);

    RequestUpdateChannelDefaultContractResult requestUpdateChannelDefaultContract(RequestUpdateChannelDefaultContractReqDTO request);

    EmployeeCardQueryResult queryEmployeeCard(EmployeeCardQueryReqDTO request);

    CommonResult activateEmployeeCard(EmployeeCardActivateReqDTO request);

    RequestRemovePayChannelResult requestRemovePayChannel(RequestRemovePayChannelReqDTO request);

    /** IF8A-42 用户销户，透传到 account-server。 */
    UserCancelResult userCancel(UserCancelReqDTO request);
}
