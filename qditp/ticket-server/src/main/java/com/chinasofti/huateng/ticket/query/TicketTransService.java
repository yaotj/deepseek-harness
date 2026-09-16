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
    RequestTransListResult requestTransList(QueryTransListReqDTO request);

    /**
     * IF8A-41 查询账单统计。
     *
     * @param request 查询条件
     * @return 账单统计结果
     */
    RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request);

    /**
     * IF8A-34 获取订单详情。
     *
     * @param request 查询条件（thirdUserId + orderNo）
     * @return 订单详情
     */
    RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request);

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
