package com.chinasofti.huateng.fep.app.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.fep.app.service.TicketAppService;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.app.ReceiveBlackListFromItpReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import com.chinasofti.huateng.rpc.transquery.TransQueryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TicketAppServiceImpl implements TicketAppService {
    private static final Logger log = LoggerFactory.getLogger(TicketAppServiceImpl.class);

    private final BlacklistClient blacklistClient;
    private final TicketClient ticketClient;
    /** 仅 IF8A-05 / IF8A-41 / IF8A-34 三个交易查询走本 Client，其余走 ticketClient。 */
    private final TransQueryClient transQueryClient;

    public TicketAppServiceImpl(BlacklistClient blacklistClient, TicketClient ticketClient,
                                TransQueryClient transQueryClient) {
        this.blacklistClient = blacklistClient;
        this.ticketClient = ticketClient;
        this.transQueryClient = transQueryClient;
    }

    @Override
    public QueryBlackListResult queryBlackList(QueryBlackListReqDTO request) {
        return blacklistClient.queryBlackList(request);
    }

    @Override
    public CommonResult receiveBlackListFromItp(ReceiveBlackListFromItpReqDTO request) {
        log.info("IF8B-03 接收黑名单结果通知, request={}", JSON.toJSONString(request));
        CommonResult result = new CommonResult();
        result.setRetCode("0000");
        result.setRetMsg("成功");
        return result;
    }

    @Override
    public QueryUserItineraryResult queryUserItinerary(QueryUserItineraryReqDTO request) {
        return ticketClient.queryUserItinerary(request);
    }

    @Override
    public RequestExcessFareResult requestExcessFare(RequestExcessFareReqDTO request) {
        log.info("call ticket requestExcessFare request={}", JSON.toJSONString(request));
        RequestExcessFareResult result = ticketClient.requestExcessFare(request);
        log.info("call ticket requestExcessFare response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public RequestTransListResult requestTransList(RequestTransListReqDTO request) {
        log.info("call transQuery requestTransList request={}", JSON.toJSONString(request));
        RequestTransListResult result = transQueryClient.requestTransList(request);
        log.info("call transQuery requestTransList response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request) {
        log.info("call transQuery requestTransStatistics request={}", JSON.toJSONString(request));
        RequestTransStatisticsResult result = transQueryClient.requestTransStatistics(request);
        log.info("call transQuery requestTransStatistics response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request) {
        log.info("call transQuery requestTransDetail request={}", JSON.toJSONString(request));
        RequestTransDetailResult result = transQueryClient.requestTransDetail(request);
        log.info("call transQuery requestTransDetail response={}", JSON.toJSONString(result));
        return result;
    }
}
