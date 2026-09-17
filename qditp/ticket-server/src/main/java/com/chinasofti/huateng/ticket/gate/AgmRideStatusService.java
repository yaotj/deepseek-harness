package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;

/** AGM（闸机）乘车状态服务。 */
public interface AgmRideStatusService {

    /**
     * IF1A-01 闸机检票通知。
     *
     * @param request 闸机检票通知业务参数
     * @return 处理结果
     */
    NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultReqDTO request);

    /**
     * 查询票卡当前状态。
     *
     * @param request 查询请求参数
     * @return 票卡状态信息
     */
    QueryStatusRespDTO queryQrCodeStatus(QueryStatusReqDTO request);

}
