package com.chinasofti.huateng.rpc.paySign;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationResult;
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
public class PaySignClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(PaySignClient.class);

    public PaySignClient(@Value("${service.paySign.url:pay-sign-service}") String baseUrl, @Value("${service.paySign.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 请求签约信息
     *
     * @return
     */

    public RequestSignInfoResult requestSignInfo(@RequestBody RequestSignInfoReqDTO request) {
        String result = postJsonAndGetResponse("/requestSignInfo", request);
        return JSONUtil.toBean(result, new TypeReference<RequestSignInfoResult>() {
        }, true);
    }

    public PaySignCallbackResult receiveSignResult(@RequestBody ReceiveSignResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receiveSignResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }

    /**
     * 支付 API 5.1 支付回调内部转发。
     */
    public PaySignCallbackResult receivePayResult(@RequestBody ReceivePayResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receivePayResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }

    /**
     * IF8A-06 请求解约。
     *
     * <p>fep-app 通过该 RPC 调用 pay-sign-server，由 pay-sign-server 组装支付平台 2.3 请求解约报文。</p>
     *
     * @param request 请求解约业务参数
     * @return 请求解约受理结果
     */
    public RequestTerminationResult requestTermination(@RequestBody RequestTerminationReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTermination", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTerminationResult>() {
        }, true);
    }

    /**
     * 支付 API 1.1 请求支付。
     *
     * <p>调用方只传业务参数，pay-sign-server 负责组装支付网关公共参数和签名。</p>
     *
     * @param request 请求支付业务参数
     * @return 请求支付结果
     */
    public RequestPayResult requestPay(@RequestBody RequestPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<RequestPayResult>() {
        }, true);
    }

    /**
     * 支付 API 3.1 请求退款。
     *
     * <p>调用方只传 orderNo/refundAmount，pay-sign-server 负责补齐退款请求参数和签名。</p>
     */
    public RequestRefundResult requestRefund(@RequestBody RequestRefundReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestRefund", request);
        return JSONUtil.toBean(result, new TypeReference<RequestRefundResult>() {
        }, true);
    }

    /**
     * 支付 API 5.3 解约回调内部转发。
     *
     * <p>支付平台先回调 fep-app，fep-app 再通过该 RPC 透传给 pay-sign-server 完成本地解约业务。</p>
     *
     * @param request 解约回调业务参数
     * @return 回调处理结果
     */
    public PaySignCallbackResult receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receiveTerminationResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }

    /**
     * 支付宝出行-添加签约信息。
     */
    public AlipayTripAddContractRespDTO alipayTripAddContract(@RequestBody AlipayTripAddContractReqDTO request) {
        String result = postJsonAndGetResponse("/channel/addContract", request);
        RequestSignInfoResult appResult = JSONUtil.toBean(result, new TypeReference<RequestSignInfoResult>() {
        }, true);
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        response.setRetCode(appResult.getRetCode());
        response.setRetMsg(appResult.getRetMsg());
        return response;
    }

    /**
     * 支付宝出行-解约登记。
     */
    public AlipayTripTerminateContractRespDTO alipayTripTerminateContract(@RequestBody AlipayTripTerminateContractReqDTO request) {
        RequestTerminationReqDTO appRequest = new RequestTerminationReqDTO();
        appRequest.setRequestSignSeq(request.getAgreementCode());
        RequestTerminationResult appResult = requestTermination(appRequest);
        AlipayTripTerminateContractRespDTO response = new AlipayTripTerminateContractRespDTO();
        response.setRetCode(appResult.getRetCode());
        response.setRetMsg(appResult.getRetMsg());
        return response;
    }

}
