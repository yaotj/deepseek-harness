package com.chinasofti.huateng.wallet.service;

import com.chinasofti.huateng.wallet.model.contract.RequestContractResultReqDTO;
import com.chinasofti.huateng.wallet.model.contract.RequestContractResultRespDTO;
import com.chinasofti.huateng.wallet.model.contract.RequestTerminationReqDTO;
import com.chinasofti.huateng.wallet.model.contract.RequestTerminationRespDTO;

public interface WalletContractService {
    /**
     * 查询签约结果。
     */
    RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request);

    /**
     * 提交解约请求。
     */
    RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request);
}
