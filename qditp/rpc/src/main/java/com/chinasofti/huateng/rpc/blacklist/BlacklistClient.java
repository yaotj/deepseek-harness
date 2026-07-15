package com.chinasofti.huateng.rpc.blacklist;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListRespDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 黑名单服务 RPC 客户端。
 */
@Service
public class BlacklistClient extends ProxyWebClient {
    /**
     * 创建黑名单服务 RPC 客户端。
     *
     * @param baseUrl 黑名单服务地址
     * @param openLogger 是否开启请求日志
     * @param webClientBuilder WebClient 构建器
     */
    public BlacklistClient(@Value("${service.blacklist.url:blacklist-service}") String baseUrl,
                           @Value("${service.blacklist.openLogger:true}") boolean openLogger,
                           WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 请求 blacklist-server 查询黑名单状态。
     *
     * @param request 查询黑名单请求参数
     * @return 查询黑名单结果
     */
    public QueryBlackListResult queryBlackList(@RequestBody QueryBlackListReqDTO request) {
        String result = postJsonAndGetResponse("/queryBlackList", request);
        return JSONUtil.toBean(result, new TypeReference<QueryBlackListResult>() {
        }, true);
    }

    /**
     * 支付宝出行-黑名单状态变更通知。
     */
    public AlipayTripReceiveBlackListRespDTO alipayTripReceiveBlackList(@RequestBody AlipayTripReceiveBlackListReqDTO request) {
        QueryBlackListReqDTO appRequest = new QueryBlackListReqDTO();
        appRequest.setCardId(request.getCardId());
        QueryBlackListResult appResult = queryBlackList(appRequest);
        AlipayTripReceiveBlackListRespDTO response = new AlipayTripReceiveBlackListRespDTO();
        response.setRetCode(appResult.getRetCode());
        response.setRetMsg(appResult.getRetMsg());
        return response;
    }
}
