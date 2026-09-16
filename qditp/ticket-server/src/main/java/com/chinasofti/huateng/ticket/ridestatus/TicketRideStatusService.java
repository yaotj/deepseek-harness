package com.chinasofti.huateng.ticket.service;

import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;

/**
 * APP 侧乘车状态服务（开户、行程查询）。
 * AGM 侧接口请使用 {@link com.chinasofti.huateng.ticket.service.AgmRideStatusService}。
 */
public interface TicketRideStatusService {
    /**
     * 开户成功后初始化用户乘车状态。
     */
    RegisterRideStatusRespDTO registerRideStatus(RegisterRideStatusReqDTO request);

    /**
     * IF8A-29 查询用户上次行程。
     *
     * @param request 查询用户上次行程请求参数
     * @return 用户当前行程信息
     */
    QueryUserItineraryResult queryUserItinerary(QueryUserItineraryReqDTO request);

    /**
     * IF8A-04 请求自助补站。
     *
     * @param request 自助补站请求参数
     * @return 自助补站结果
     */
    RequestExcessFareResult requestExcessFare(RequestExcessFareReqDTO request);

    /**
     * 查询最近一次进站设备编号。
     */
    String queryEntryDevice(String cardId);
}
