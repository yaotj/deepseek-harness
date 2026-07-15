package com.chinasofti.huateng.rpc.alipay.paysign;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 支付宝出行-签约服务 RPC 客户端。
 */
@Service
public class AlipayPaySignClient extends ProxyWebClient {

    public AlipayPaySignClient(@Value("${service.alipay-pay-sign.url:alipay-pay-sign-service}") String baseUrl,
                               @Value("${service.alipay-pay-sign.openLogger:true}") boolean openLogger,
                               WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 添加签约信息。
     */
    public AlipayTripAddContractRespDTO alipayTripAddContract(@RequestBody AlipayTripAddContractReqDTO request) {
        String result = postJsonAndGetResponse("/channel/addContract", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripAddContractRespDTO>() {
        }, true);
    }

    /**
     * 支付宝出行-解约登记。
     */
    public AlipayTripTerminateContractRespDTO alipayTripTerminateContract(@RequestBody AlipayTripTerminateContractReqDTO request) {
        String result = postJsonAndGetResponse("/channel/terminateContract", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripTerminateContractRespDTO>() {
        }, true);
    }

    /**
     * 支付宝出行-支付申请。
     */
    public AlipayTripRequestPayRespDTO alipayTripRequestPay(@RequestBody AlipayTripRequestPayReqDTO request) {
        String result = postJsonAndGetResponse("/api/payment/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripRequestPayRespDTO>() {
        }, true);
    }

    /**
     * 支付宝出行-支付结果查询。
     */
    public AlipayTripPayQueryRespDTO alipayTripPayQuery(@RequestBody AlipayTripPayQueryReqDTO request) {
        String result = postJsonAndGetResponse("/api/payment/payQuery", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripPayQueryRespDTO>() {
        }, true);
    }

    /**
     * 支付宝出行-退款申请。
     */
    public AlipayTripRequestRefundRespDTO alipayTripRequestRefund(@RequestBody AlipayTripRequestRefundReqDTO request) {
        String result = postJsonAndGetResponse("/api/payment/requestRefund", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripRequestRefundRespDTO>() {
        }, true);
    }

    /**
     * 查询用户签约信息。
     */
    public AlipaySignInfoDTO selectSignInfo(String thirdUserId) {
        String url = "/channel/selectSignInfo?thirdUserId=" + thirdUserId;
        String result = getAndGetResponse(url, new java.util.HashMap<>());
        if (result == null || result.isEmpty()) {
            return null;
        }
        cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof cn.hutool.json.JSONObject) ? ((cn.hutool.json.JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, AlipaySignInfoDTO.class);
    }

    /**
     * 支付宝出行-支付结果回调。
     */
    public com.chinasofti.huateng.common.response.AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request) {
        String result = postJsonAndGetResponse("/api/payment/payNotify", request);
        if (result == null || result.isEmpty()) {
            return null;
        }
        cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof cn.hutool.json.JSONObject) ? ((cn.hutool.json.JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, com.chinasofti.huateng.common.response.AlipayCommonResponse.class);
    }
}
