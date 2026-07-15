package com.chinasofti.huateng.fep.dev.service;

import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusRespDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;

/**
 * 设备请求处理服务。
 */
public interface DevService {

    /**
     * IF1A-04 查询票卡状态。
     *
     * @param request 查询票卡状态业务参数
     * @return 查询票卡状态响应
     */
    RequestQrCodeStatusRespDTO requestQrCodeStatus(RequestQrCodeStatusReqDTO request);

    /**
     * IF1A-01 闸机检票通知。
     *
     * @param request 闸机检票通知业务参数
     * @return 处理结果
     */
    NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultReqDTO request);
}
