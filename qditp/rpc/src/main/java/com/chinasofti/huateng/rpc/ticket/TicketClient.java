package com.chinasofti.huateng.rpc.ticket;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.ticket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.time.Duration;

@Service
public class TicketClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(TicketClient.class);

    public TicketClient(@Value("${service.ticket.url:ticket-service}") String baseUrl, @Value("${service.ticket.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofSeconds(30);
    }

    public RegisterRideStatusRespDTO registerRideStatus(@RequestBody RegisterRideStatusReqDTO request) {
        Map<String, String> requestBody = new HashMap<>();
        String result = postJsonAndGetResponse("/ci/app/registerRideStatus", request);
        return JSONUtil.toBean(result, new TypeReference<RegisterRideStatusRespDTO>() {
        }, true);
    }

    public QueryStatusRespDTO queryQrCodeStatus(@RequestBody QueryStatusReqDTO request) {
        Map<String, String> requestBody = new HashMap<>();
        String result = postJsonAndGetResponse("/ci/app/queryQrCodeStatus", request);
        return JSONUtil.toBean(result, new TypeReference<QueryStatusRespDTO>() {
        }, true);
    }

    public QueryUserItineraryResult queryUserItinerary(@RequestBody QueryUserItineraryReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/queryUserItinerary", request);
        return JSONUtil.toBean(result, new TypeReference<QueryUserItineraryResult>() {
        }, true);
    }

    public RequestExcessFareResult requestExcessFare(@RequestBody RequestExcessFareReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestExcessFare", request);
        return JSONUtil.toBean(result, new TypeReference<RequestExcessFareResult>() {
        }, true);
    }

    public RequestTransListResult requestTransList(@RequestBody RequestTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTransList", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransListResult>() {
        }, true);
    }

    public RequestTransStatisticsResult requestTransStatistics(@RequestBody RequestTransStatisticsReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTransStatistics", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransStatisticsResult>() {
        }, true);
    }

    public NotifyVerifyResultRespDTO notifyVerifyResult(@RequestBody NotifyVerifyResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/agm/notiVerifyResult", request);
        return JSONUtil.toBean(result, new TypeReference<NotifyVerifyResultRespDTO>() {
        }, true);
    }

    /**
     * 支付宝出行-查询乘车记录。
     */
    public AlipayTripFindTravelListRespDTO alipayTripFindTravelList(@RequestBody AlipayTripFindTravelListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/channel/findTravelList", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripFindTravelListRespDTO>() {
        }, true);
    }

    /**
     * 支付宝出行-查询乘车记录详情。
     */
    public AlipayTripFindTravelDetailRespDTO alipayTripFindTravelDetail(@RequestBody AlipayTripFindTravelDetailReqDTO request) {
        String result = postJsonAndGetResponse("/ci/channel/findTravelDetail", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripFindTravelDetailRespDTO>() {
        }, true);
    }

    /**
     * 查询最近一次进站设备编号。
     */
    public String queryEntryDevice(String cardId) {
        String url = "/ci/app/queryEntryDevice?cardId=" + cardId;
        String result = getAndGetResponse(url, new java.util.HashMap<>());
        if (result == null || result.isEmpty()) {
            return null;
        }
        String trimmed = result.trim();
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(trimmed);
        Object data = wrapper.get("data");
        if (data instanceof cn.hutool.json.JSONObject) {
            return ((cn.hutool.json.JSONObject) data).getStr("data");
        }
        if (data != null) {
            return data.toString();
        }
        return trimmed;
    }

    /**
     * IF5A-01 请求票卡分析。
     */
    public RequestCardDataAnalyseRespDTO requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestCardDataAnalyse", request);
        return JSONUtil.toBean(result, new TypeReference<RequestCardDataAnalyseRespDTO>() {
        }, true);
    }

    /**
     * IF5A-03 请求票卡更新。
     */
    public RequestCardDataUpdateRespDTO requestUpdateCardData(RequestCardDataUpdateReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestUpdateCardData", request);
        return JSONUtil.toBean(result, new TypeReference<RequestCardDataUpdateRespDTO>() {
        }, true);
    }
}
