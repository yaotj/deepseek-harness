package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryRespDTO;


public interface AlipayQueryService {


    AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request);


    AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request);


    AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request);
}
