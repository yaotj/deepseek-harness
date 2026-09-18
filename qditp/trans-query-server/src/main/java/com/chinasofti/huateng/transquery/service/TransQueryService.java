package com.chinasofti.huateng.transquery.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespVO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;

/** 交易查询服务对外接口（IF8A-05 / IF8A-34 / IF8A-41 + 支付宝出行行程查询）。 */
public interface TransQueryService {

    /** IF8A-05 查询交易记录列表。 */
    RequestTransListResult requestTransList(QueryTransListReqDTO request);

    /** IF8A-41 查询账单统计。 */
    RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request);

    /** IF8A-34 获取订单详情。 */
    RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request);

    /** 支付宝出行-查询乘车记录列表。 */
    AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request);

    /** 支付宝出行-查询乘车记录详情。 */
    AlipayTripFindTravelDetailRespVO findTravelDetail(AlipayTripFindTravelDetailReqDTO request);
}
