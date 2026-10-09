package com.chinasofti.huateng.rpc.transquery;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * trans-query-server（交易查询服务，9113）客户端。
 */
@Service
public class TransQueryClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(TransQueryClient.class);

    public TransQueryClient(@Value("${service.transQuery.url:trans-query-service}") String baseUrl,
                            @Value("${service.transQuery.openLogger:true}") boolean openLogger,
                            WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofSeconds(10);
    }

    /** IF8A-05 请求查询交易记录。 */
    public RequestTransListResult requestTransList(@RequestBody RequestTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTransList", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransListResult>() {
        }, true);
    }

    /** IF8A-41 查询账单统计。 */
    public RequestTransStatisticsResult requestTransStatistics(@RequestBody RequestTransStatisticsReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTransStatistics", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransStatisticsResult>() {
        }, true);
    }

    /** IF8A-34 获取订单详情。 */
    public RequestTransDetailResult requestTransDetail(@RequestBody RequestTransDetailReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTransDetail", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransDetailResult>() {
        }, true);
    }

    /** 支付宝出行-查询乘车记录列表。 */
    public AlipayTripFindTravelListRespDTO findTravelList(@RequestBody AlipayTripFindTravelListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/alipay/travel/list", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripFindTravelListRespDTO>() {
        }, true);
    }

    /**
     * 支付宝出行-查询乘车记录详情。
     *
     * <p><b>应答是扁平结构</b>（R6 §3.72 表148：`retCode` / `retMsg` + 19 个业务字段直接铺在顶层），
     * <b>NEVER 再包一层 `data`</b> —— 原先返回的三层 VO（`retCode` / `retMsg` / `data`）与契约不符，
     * 支付宝按表148 取 `entryStationName` 等字段会全部取到 `null`，2026-09-20 已按裁决拆平（ADR-D148）。
     */
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(@RequestBody AlipayTripFindTravelDetailReqDTO request) {
        String result = postJsonAndGetResponse("/ci/alipay/travel/detail", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripFindTravelDetailRespDTO>() {
        }, true);
    }
}
