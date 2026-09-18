package com.chinasofti.huateng.transquery.service.impl;

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
import com.chinasofti.huateng.transquery.query.AlipayTravelQueryHandler;
import com.chinasofti.huateng.transquery.query.TransDetailQueryHandler;
import com.chinasofti.huateng.transquery.query.TransListQueryHandler;
import com.chinasofti.huateng.transquery.query.TransStatisticsQueryHandler;
import com.chinasofti.huateng.transquery.service.TransQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 交易查询编排壳 —— 每个入口各自委托给独立 Handler，本类不写任何业务逻辑。 */
@Service
public class TransQueryServiceImpl implements TransQueryService {

    @Autowired
    private TransListQueryHandler transListQueryHandler;

    @Autowired
    private TransStatisticsQueryHandler transStatisticsQueryHandler;

    @Autowired
    private TransDetailQueryHandler transDetailQueryHandler;

    @Autowired
    private AlipayTravelQueryHandler alipayTravelQueryHandler;

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
    public AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request) {
        return alipayTravelQueryHandler.findTravelList(request);
    }

    @Override
    public AlipayTripFindTravelDetailRespVO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        return alipayTravelQueryHandler.findTravelDetail(request);
    }
}
