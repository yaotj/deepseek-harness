package com.chinasofti.huateng.rpc.facepay;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.pay.SupplementOrderReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * face-pay-server RPC 客户端。
 */
@Service
public class FacePayClient extends ProxyWebClient {

    public FacePayClient(@Value("${service.facePay.url:face-pay-service}") String baseUrl,
                         @Value("${service.facePay.openLogger:true}") boolean openLogger,
                         WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    // ==================== IF8A-26 APP 在线补款下单 RPC ====================

    /**
     * IF8A-26 请求补款下单（供 fep-app-server 调用）。
     */
    public SupplementOrderRespDTO requestPayOrder(@RequestBody SupplementOrderReqDTO request) {
        String result = postJsonAndGetResponse("/ci/facePay/app/requestPayOrder", request);
        return JSONUtil.toBean(result, new TypeReference<SupplementOrderRespDTO>() {
        }, true);
    }
}
