package com.chinasofti.huateng.rpc.pay;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class GateTxnPayClient extends ProxyWebClient {
    public GateTxnPayClient(@Value("${service.gateTxnPay.url:gate-txn-pay-service}") String baseUrl,
                            @Value("${service.gateTxnPay.openLogger:true}") boolean openLogger,
                            WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    public GateTxnPayRespDTO requestGateTxnPay(@RequestBody GateTxnPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayRespDTO>() {
        }, true);
    }
}
