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
 *
 * <p>2026-09-15 补款功能从 gate-txn-pay-server 迁入 face-pay-server，
 * IF8A-26 补款下单改走本客户端；GateTxnPayClient 中同名方法已删除。</p>
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
     *
     * <p>只生成补款单并返回补款单号，不发起支付。调用方 MUST 检查 retCode：
     * 8001 为参数问题、8003 为订单状态/金额校验不通过，两者都不应重试。</p>
     */
    public SupplementOrderRespDTO requestPayOrder(@RequestBody SupplementOrderReqDTO request) {
        String result = postJsonAndGetResponse("/ci/facePay/app/requestPayOrder", request);
        return JSONUtil.toBean(result, new TypeReference<SupplementOrderRespDTO>() {
        }, true);
    }
}
