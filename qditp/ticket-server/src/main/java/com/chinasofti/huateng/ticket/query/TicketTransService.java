package com.chinasofti.huateng.ticket.query;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransListResult;

public interface TicketTransService {

    RequestTransListResult requestTransList(QueryTransListReqDTO request);


    RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request);


    RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request);


    AlipayTripFindTravelListRespDTO alipayTripFindTravelList(AlipayTripFindTravelListReqDTO request);


    AlipayTripFindTravelDetailRespDTO alipayTripFindTravelDetail(AlipayTripFindTravelDetailReqDTO request);
}
