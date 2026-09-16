package com.chinasofti.huateng.ticket.service.impl;

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
import com.chinasofti.huateng.ticket.service.TicketTransService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 交易记录查询服务实现（薄壳编排层）。
 *
 * <p>委托各 Handler 处理具体业务逻辑：
 * <ul>
 *   <li>{@link TransQueryHandler} - 交易记录查询业务 (IF8A-05/34/41)</li>
 *   <li>{@link AlipayTripHandler} - 支付宝行程查询业务</li>
 * </ul>
 */
@Service
public class TicketTransServiceImpl implements TicketTransService {

    @Autowired
    private TransQueryHandler transQueryHandler;

    @Autowired
    private AlipayTripHandler alipayTripHandler;

    @Override
    public RequestTransListResult requestTransList(QueryTransListReqDTO request) {
        return transQueryHandler.requestTransList(request);
    }

    @Override
    public RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request) {
        return transQueryHandler.requestTransStatistics(request);
    }

    @Override
    public RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request) {
        return transQueryHandler.requestTransDetail(request);
    }

    @Override
    public AlipayTripFindTravelListRespDTO alipayTripFindTravelList(AlipayTripFindTravelListReqDTO request) {
        return alipayTripHandler.alipayTripFindTravelList(request);
    }

    @Override
    public AlipayTripFindTravelDetailRespDTO alipayTripFindTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        return alipayTripHandler.alipayTripFindTravelDetail(request);
    }
}
