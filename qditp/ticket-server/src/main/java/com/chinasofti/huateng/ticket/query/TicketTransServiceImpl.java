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

/**
 * 交易记录查询服务实现（薄壳编排层）。
 *
 * <p>委托各 Handler 处理具体业务逻辑：
 * <ul>
 *   <li>{@link TransListQueryHandler} - IF8A-05 交易记录列表</li>
 *   <li>{@link TransStatisticsQueryHandler} - IF8A-41 账单统计</li>
 *   <li>{@link TransDetailQueryHandler} - IF8A-34 订单详情</li>
 *   <li>{@link AlipayTripHandler} - 支付宝行程查询业务</li>
 * </ul>
 *
 * <p>2026-09-14 前三者是同一个 626 行的 {@code TransQueryHandler}，已按入口拆开。
 * <b>本类是 {@code query} 包对外的唯一门面</b>：包外 MUST 只依赖 {@link TicketTransService}，
 * <b>NEVER 直接注入上面任何一个 Handler</b>。
 */
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
