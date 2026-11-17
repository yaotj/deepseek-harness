package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;

public interface TicketRideStatusService {

    RegisterRideStatusRespDTO registerRideStatus(RegisterRideStatusReqDTO request);


    QueryUserItineraryResult queryUserItinerary(QueryUserItineraryReqDTO request);
}
