package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.paysign.model.request.CheckFailedOrdersReqDTO;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;

/**
 * ITP 解约内部接口服务。
 */
public interface TerminationInternalService {

    /**
     * 查询用户是否存在扣费失败订单。
     */
    CheckFailedOrdersRespDTO checkFailedOrders(CheckFailedOrdersReqDTO request);

    /**
     * 执行支付平台解约。
     */
    BaseRespDTO executeTermination(ExecuteTerminationReqDTO request);

    /**
     * 通知 APP 解约失败。
     */
    BaseRespDTO notifyTerminationFailed(NotifyTerminationFailedReqDTO request);
}
