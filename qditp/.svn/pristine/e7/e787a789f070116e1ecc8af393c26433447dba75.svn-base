package com.chinasofti.huateng.ticket.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.ticket.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.ticket.model.app.RequestTransListResult;

/**
 * IF8A-05 交易记录查询服务接口。
 */
public interface TicketTransService {
    /**
     * 查询交易记录列表。
     *
     * @param request 查询条件
     * @return 交易记录分页结果
     */
    RequestTransListResult requestTransList(RequestTransListReqDTO request);

    /**
     * 支付宝出行-查询乘车记录列表。
     *
     * @param request 查询条件
     * @return 支付宝乘车记录分页结果
     */
    AlipayTripFindTravelListRespDTO alipayTripFindTravelList(AlipayTripFindTravelListReqDTO request);

    /**
     * 支付宝出行-查询乘车记录详情。
     *
     * @param request 查询条件
     * @return 支付宝乘车记录详情
     */
    AlipayTripFindTravelDetailRespDTO alipayTripFindTravelDetail(AlipayTripFindTravelDetailReqDTO request);
}
