package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespVO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryRespDTO;

/**
 * 支付宝出行查询服务接口。
 */
public interface AlipayQueryService {

    /**
     * 查询乘车记录列表。
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request);

    /**
     * 查询乘车记录详情。
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripFindTravelDetailRespVO findTravelDetail(AlipayTripFindTravelDetailReqDTO request);

    /**
     * 支付结果查询。
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request);
}
