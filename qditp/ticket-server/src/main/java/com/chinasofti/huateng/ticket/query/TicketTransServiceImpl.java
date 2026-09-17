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
import com.chinasofti.huateng.ticket.alipay.AlipayTripHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 交易记录查询服务实现（薄壳编排层）。 */
@Service
public class TicketTransServiceImpl implements TicketTransService {

    @Autowired
    private TransListQueryHandler transListQueryHandler;

    @Autowired
    private TransStatisticsQueryHandler transStatisticsQueryHandler;

    @Autowired
    private TransDetailQueryHandler transDetailQueryHandler;

    @Autowired
    private AlipayTripHandler alipayTripHandler;

    @Override
    public RequestTransListResult requestTransList(QueryTransListReqDTO request) {
        return transListQueryHandler.requestTransList(request);
    }

    @Override
    public RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request) {
        return transStatisticsQueryHandler.requestTransStatistics(request);
    }

    @Override
    public RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request) {
        return transDetailQueryHandler.requestTransDetail(request);
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
