package com.chinasofti.huateng.rpc.alipay.account;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝出行-账户服务 RPC 客户端。
 */
@Service
public class AlipayAccountClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(AlipayAccountClient.class);

    public AlipayAccountClient(@Value("${service.account.url:http://127.0.0.1:9106}") String baseUrl,
                               @Value("${service.account.openLogger:true}") boolean openLogger,
                               WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 支付宝出行-开卡申请。
     */
    public AlipayTripRequestApplicationRespDTO alipayTripRequestApplication(@RequestBody AlipayTripRequestApplicationReqDTO request) {
        String result = postJsonAndGetResponse("/channel/requestApplication", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripRequestApplicationRespDTO>() {
        }, true);
    }

    /**
     * 根据 thirdUserId 查询支付宝用户信息。
     */
    public AlipayUserInfoDTO selectByThirdUserId(String thirdUserId) {
        String url = "/channel/queryUserInfo?thirdUserId=" + thirdUserId;
        Map<String, String> params = new HashMap<>();
        String result = getAndGetResponse(url, params);
        if (result == null || result.isEmpty()) {
            return null;
        }
        cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
        // 兼容两种返回格式：裸对象 或 {"code":"...","msg":null,"data":{...}}
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof cn.hutool.json.JSONObject) ? ((cn.hutool.json.JSONObject) data).toString() : result;
        AlipayUserInfoDTO dto = JSONUtil.toBean(parseTarget, AlipayUserInfoDTO.class);
        if (dto != null && dto.getCardId() != null && !dto.getCardId().isEmpty()) {
            return dto;
        }
        return null;
    }

    /**
     * 更新用户支付通道信息。
     */
    public boolean updatePaymentChannel(String thirdUserId, String thirdPayId, String reqContractNo) {
        String url = "/channel/updatePaymentChannel?thirdUserId=" + thirdUserId
                + "&thirdPayId=" + thirdPayId
                + "&reqContractNo=" + reqContractNo;
        Map<String, String> params = new HashMap<>();
        String result = getAndGetResponse(url, params);
        if (result == null || result.isEmpty()) {
            return false;
        }
        // 兼容两种返回格式：裸 true/false 或 {"data":true/false}
        result = result.trim();
        if (result.startsWith("{")) {
            cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
            Object data = wrapper.get("data");
            return data != null && Boolean.parseBoolean(data.toString());
        }
        return Boolean.parseBoolean(result);
    }
}
