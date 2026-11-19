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
import com.chinasofti.huateng.model.app.ReceiveRefundResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultResult;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationResult;
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.PaySignInfoDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;



@Service
public class PaySignClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(PaySignClient.class);

    public PaySignClient(@Value("${service.paySign.url:pay-sign-service}") String baseUrl, @Value("${service.paySign.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected boolean isNetworkRetryEnabled() {
        return false;
    }

    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofSeconds(30);
    }



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


    public PaySignCallbackResult receivePayResult(@RequestBody ReceivePayResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receivePayResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }


    public PaySignCallbackResult receiveRefundResult(@RequestBody ReceiveRefundResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receiveRefundResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }

    public RequestTerminationResult requestTermination(@RequestBody RequestTerminationReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTermination", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTerminationResult>() {
        }, true);
    }


    public UnbindAgreementResult unbindAgreement(@RequestBody UnbindAgreementReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/unbindAgreement", request);
        return JSONUtil.toBean(result, new TypeReference<UnbindAgreementResult>() {
        }, true);
    }


    public RequestPayResult requestPay(@RequestBody RequestPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<RequestPayResult>() {
        }, true);
    }


    public RequestRefundResult requestRefund(@RequestBody RequestRefundReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestRefund", request);
        return JSONUtil.toBean(result, new TypeReference<RequestRefundResult>() {
        }, true);
    }


    public PaySignCallbackResult receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/receiveTerminationResult", request);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }


    public AlipayTripAddContractRespDTO alipayTripAddContract(@RequestBody AlipayTripAddContractReqDTO request) {
        String result = postJsonAndGetResponse("/channel/addContract", request);
        RequestSignInfoResult appResult = JSONUtil.toBean(result, new TypeReference<RequestSignInfoResult>() {
        }, true);
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        response.setRetCode(appResult.getRetCode());
        response.setRetMsg(appResult.getRetMsg());
        return response;
    }


    public AlipayTripTerminateContractRespDTO alipayTripTerminateContract(@RequestBody AlipayTripTerminateContractReqDTO request) {
        RequestTerminationReqDTO appRequest = new RequestTerminationReqDTO();
        appRequest.setRequestSignSeq(request.getAgreementCode());
        RequestTerminationResult appResult = requestTermination(appRequest);
        AlipayTripTerminateContractRespDTO response = new AlipayTripTerminateContractRespDTO();
        response.setRetCode(appResult.getRetCode());
        response.setRetMsg(appResult.getRetMsg());
        return response;
    }


    public PaySignInfoDTO querySignInfoBySeq(String requestSignSeq) {
        String result = getAndGetResponse("/querySignInfoBySeq?requestSignSeq=" + requestSignSeq, new java.util.HashMap<>());
        return JSONUtil.toBean(result, PaySignInfoDTO.class, true);
    }


    public RequestContractResultResult requestContractResult(RequestContractResultReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestContractResult", request);
        return JSONUtil.toBean(result, RequestContractResultResult.class, true);
    }


    public RequestPayTxnBatchResult queryPayTxnBatch(@RequestBody com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/queryPayTxnBatch", request);
        RequestPayTxnBatchResult batchResult = JSONUtil.toBean(result, new TypeReference<RequestPayTxnBatchResult>() {
        }, true);
        if (batchResult == null) {
            RequestPayTxnBatchResult errorResult = new RequestPayTxnBatchResult();
            errorResult.setRetCode("9999");
            errorResult.setRetMsg("批量查询支付明细响应为空");
            return errorResult;
        }
        if (!"0000".equals(batchResult.getRetCode())) {
            String originalRetCode = batchResult.getRetCode();
            batchResult.setRetCode("9999");
            String originalRetMsg = batchResult.getRetMsg();
            if (originalRetMsg == null || originalRetMsg.isEmpty()) {
                batchResult.setRetMsg("批量查询支付明细失败 [" + originalRetCode + "]");
            } else {
                batchResult.setRetMsg(originalRetMsg + " [" + originalRetCode + "]");
            }
        }
        return batchResult;
    }


    public ProcessTerminationRespDTO processTermination(@RequestBody ProcessTerminationReqDTO request) {
        return processTermination(request, null);
    }


    public ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request, Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/termination/process", request, headers);
        return JSONUtil.toBean(result, new TypeReference<ProcessTerminationRespDTO>() {
        }, true);
    }


    public CompensateNotifyRespDTO compensateSignNotify(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/paySign/compensateNotify", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }


    public CompensateNotifyRespDTO compensateTerminationNotify(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/termination/compensateNotify", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }


    public CompensateNotifyRespDTO compensateRefundQuery(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/payment/compensateRefundQuery", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }


    public CompensateNotifyRespDTO compensateRefundSummary(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/payment/compensateRefundSummary", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }


    public CompensateNotifyRespDTO compensatePayStatus(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/payment/compensatePayStatus", new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<CompensateNotifyRespDTO>() {
        }, true);
    }


    public PaySignCallbackResult replayPaymentCallback(String merchantOrderNo, Map<String, String> headers) {
        String result = postJsonAndGetResponse(
                "/internal/payment/replayCallback?merchantOrderNo=" + merchantOrderNo,
                new java.util.HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<PaySignCallbackResult>() {
        }, true);
    }


    public RpcOutcome registerCompletedTxn(@RequestBody RegisterCompletedPayTxnReqDTO request) {
        String result;
        try {
            result = postJsonAndGetResponse("/internal/payment/registerCompletedTxn", request);
        } catch (Exception e) {
            log.warn("登记已完成支付流水未获业务答复（可重试）, orderNo={}", request.getOrderNo(), e);
            return new RpcOutcome.Unreachable(e);
        }
        if (result == null || result.isEmpty()) {
            return new RpcOutcome.BizRejected(null, "支付域响应体为空");
        }
        try {
            cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
            return RpcOutcome.ofRetCode(wrapper.getStr("retCode"), wrapper.getStr("retMsg"));
        } catch (Exception e) {
            log.warn("登记已完成支付流水响应体非JSON（按业务拒绝处理，重推同一报文不会变好）, orderNo={}, result={}",
                    request.getOrderNo(), result, e);
            return new RpcOutcome.BizRejected(null, "支付域响应体非JSON");
        }
    }


    @Deprecated
    public boolean updatePaySignDisplayAccount(String thirdUserId, String displayAccount) {
        return updateDisplayAccountOutcome(thirdUserId, displayAccount).isOk();
    }


    public RpcOutcome updateDisplayAccountOutcome(String thirdUserId, String displayAccount) {
        String url = "/ci/app/updateDisplayAccount?thirdUserId=" + thirdUserId + "&displayAccount=" + displayAccount;
        String result;
        try {
            result = getAndGetResponse(url, new java.util.HashMap<>());
        } catch (Exception e) {
            log.warn("更新签约展示账号未获业务答复（可重试）, thirdUserId={}", thirdUserId, e);
            return new RpcOutcome.Unreachable(e);
        }
        if (result == null || result.isEmpty()) {
            return new RpcOutcome.BizRejected(null, "支付域响应体为空");
        }
        try {
            cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
            return RpcOutcome.ofRetCode(wrapper.getStr("retCode"), wrapper.getStr("retMsg"));
        } catch (Exception e) {
            log.warn("更新签约展示账号响应体非JSON（按业务拒绝处理，重推同一报文不会变好）, thirdUserId={}, result={}",
                    thirdUserId, result, e);
            return new RpcOutcome.BizRejected(null, "支付域响应体非JSON");
        }
    }
}
