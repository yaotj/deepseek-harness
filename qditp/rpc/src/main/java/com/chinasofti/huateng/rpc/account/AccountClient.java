package com.chinasofti.huateng.rpc.account;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestAddPayChannelResult;
import com.chinasofti.huateng.model.app.RequestApplicationReqDTO;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestRemovePayChannelResult;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO;
import com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelResult;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author zzm
 * @date 2026/5/13 11:01
 */

@Service
public class AccountClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(AccountClient.class);

    public AccountClient(@Value("${service.account.url:http://127.0.0.1:9098}") String baseUrl, @Value("${service.account.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * account 注册
     *
     * @return
     */

    public RequestApplicationResult requestApplication(@RequestBody RequestApplicationReqDTO request) {
        String result = postJsonAndGetResponse("/requestApplication", request);
        return JSONUtil.toBean(result, new TypeReference<RequestApplicationResult>() {
        }, true);
    }

    public RequestAddPayChannelResult requestAddPayChannel(@RequestBody RequestAddPayChannelReqDTO request) {
        String result = postJsonAndGetResponse("/requestAddPayChannel", request);
        return JSONUtil.toBean(result, new TypeReference<RequestAddPayChannelResult>() {
        }, true);
    }

    public RequestSetDefaultPayChannelResult requestSetDefaultPayChannel(@RequestBody RequestSetDefaultPayChannelReqDTO request) {
        String result = postJsonAndGetResponse("/requestSetDefaultPayChannel", request);
        return JSONUtil.toBean(result, new TypeReference<RequestSetDefaultPayChannelResult>() {
        }, true);
    }

    public RequestRemovePayChannelResult requestRemovePayChannel(@RequestBody RequestRemovePayChannelReqDTO request) {
        String result = postJsonAndGetResponse("/requestRemovePayChannel", request);
        return JSONUtil.toBean(result, new TypeReference<RequestRemovePayChannelResult>() {
        }, true);
    }

    public QueryUserInfoResult queryUserInfo(@RequestBody QueryUserInfoReqDTO request) {
        String result = postJsonAndGetResponse("/queryUserInfo", request);
        return JSONUtil.toBean(result, new TypeReference<QueryUserInfoResult>() {
        }, true);
    }

    /**
     * 支付宝出行-开卡申请。
     */
    public AlipayTripRequestApplicationRespDTO alipayTripRequestApplication(@RequestBody AlipayTripRequestApplicationReqDTO request) {
        String result = postJsonAndGetResponse("/channel/requestApplication", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripRequestApplicationRespDTO>() {
        }, true);
    }

}
