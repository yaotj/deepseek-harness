package com.chinasofti.huateng.ticket.service;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;

/**
 * AGM（闸机）乘车状态服务。
 *
 * <p>承接 fep-dev-server 转发的闸机侧请求，包含以下接口：
 * <ul>
 *   <li>IF1A-01 闸机检票通知</li>
 *   <li>查询票卡当前状态</li>
 *   <li>IF5A-01 票卡分析（BOM操作辅助）</li>
 *   <li>IF5A-03 票卡更新（BOM补进站/补出站）</li>
 * </ul>
 */
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

    /**
     * IF5A-01 请求票卡分析。
     *
     * @param request 票卡分析请求参数
     * @return 建议操作及票卡状态
     */
    RequestCardDataAnalyseRespDTO requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request);

    /**
     * IF5A-03 请求票卡更新。
     *
     * @param request 票卡更新请求参数
     * @return 票卡更新结果
     */
    RequestCardDataUpdateRespDTO requestCardDataUpdate(RequestCardDataUpdateReqDTO request);
}
