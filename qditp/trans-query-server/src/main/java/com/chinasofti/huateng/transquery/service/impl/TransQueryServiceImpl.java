package com.chinasofti.huateng.transquery.service.impl;

import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.transquery.query.TransDetailQueryHandler;
import com.chinasofti.huateng.transquery.query.TransListQueryHandler;
import com.chinasofti.huateng.transquery.query.TransStatisticsQueryHandler;
import com.chinasofti.huateng.transquery.service.TransQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 交易查询编排壳 —— 三个入口各自委托给独立 Handler，本类不写任何业务逻辑。 */
@Service
public class TransQueryServiceImpl implements TransQueryService {

    @Autowired
    private TransListQueryHandler transListQueryHandler;

    @Autowired
    private TransStatisticsQueryHandler transStatisticsQueryHandler;

    @Autowired
    private TransDetailQueryHandler transDetailQueryHandler;

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
}
