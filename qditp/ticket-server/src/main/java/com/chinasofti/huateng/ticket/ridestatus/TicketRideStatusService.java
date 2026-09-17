package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;

/** APP 侧乘车码状态服务。 */
public interface TicketRideStatusService {

    /** 开户成功后初始化用户乘车状态（开卡复位）。 */
    RegisterRideStatusRespDTO registerRideStatus(RegisterRideStatusReqDTO request);

    /**
     * IF8A-29 查询用户上次行程。
     *
     * @param request 查询用户上次行程请求参数
     * @return 用户当前行程信息
     */
    QueryUserItineraryResult queryUserItinerary(QueryUserItineraryReqDTO request);
}
