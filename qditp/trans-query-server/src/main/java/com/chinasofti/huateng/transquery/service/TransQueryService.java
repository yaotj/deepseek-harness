package com.chinasofti.huateng.transquery.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;

public interface TransQueryService {

    RequestTransListResult requestTransList(QueryTransListReqDTO request);

    RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request);

    RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request);

    AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request);

    AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request);
}
