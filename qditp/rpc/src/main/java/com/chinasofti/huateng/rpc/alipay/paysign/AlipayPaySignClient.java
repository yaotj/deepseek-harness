package com.chinasofti.huateng.rpc.alipay.paysign;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayTxnBriefDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayPayLogDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;


@Service
public class AlipayPaySignClient extends ProxyWebClient {

    private static final int PAY_TXN_BRIEF_MAX_SIZE = 1000;

    public AlipayPaySignClient(@Value("${service.alipay-pay-sign.url:http://alipay-pay-sign-server:8080}") String baseUrl,
                               @Value("${service.alipay-pay-sign.openLogger:true}") boolean openLogger,
                               WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected boolean isNetworkRetryEnabled() {
        return false;
    }


    public AlipayTripAddContractRespDTO alipayTripAddContract(@RequestBody AlipayTripAddContractReqDTO request) {
        String result = postJsonAndGetResponse("/channel/addContract", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripAddContractRespDTO>() {
        }, true);
    }


    public AlipayTripTerminateContractRespDTO alipayTripTerminateContract(@RequestBody AlipayTripTerminateContractReqDTO request) {
        String result = postJsonAndGetResponse("/channel/terminateContract", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripTerminateContractRespDTO>() {
        }, true);
    }


    public AlipayTripRequestPayRespDTO alipayTripRequestPay(@RequestBody AlipayTripRequestPayReqDTO request) {
        String result = postJsonAndGetResponse("/api/payment/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripRequestPayRespDTO>() {
        }, true);
    }


    public AlipayTripPayQueryRespDTO alipayTripPayQuery(@RequestBody AlipayTripPayQueryReqDTO request) {
        String result = postJsonAndGetResponse("/internal/alipay/payment/payQuery", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripPayQueryRespDTO>() {
        }, true);
    }


    public AlipayTripRequestRefundRespDTO alipayTripRequestRefund(@RequestBody AlipayTripRequestRefundReqDTO request) {
        String result = postJsonAndGetResponse("/internal/alipay/payment/requestRefund", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripRequestRefundRespDTO>() {
        }, true);
    }


    public AlipayTripTxnRefundRespDTO alipayTripTxnRefund(@RequestBody AlipayTripTxnRefundReqDTO request) {
        String result = postJsonAndGetResponse("/internal/alipay/payment/requestTxnRefund", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripTxnRefundRespDTO>() {
        }, true);
    }


    public AlipaySignInfoDTO selectSignInfo(String thirdUserId) {
        String url = "/channel/selectSignInfo?thirdUserId=" + thirdUserId;
        String result = getAndGetResponse(url, new java.util.HashMap<>());
        if (result == null || result.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, AlipaySignInfoDTO.class);
    }


    public com.chinasofti.huateng.common.response.AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request) {
        String result = postJsonAndGetResponse("/api/payment/payNotify", request);
        if (result == null || result.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, com.chinasofti.huateng.common.response.AlipayCommonResponse.class);
    }


    public java.util.Map<String, Object> selectAlipayPayLogList(java.util.Map<String, Object> params) {
        String result = postJsonAndGetResponse("/api/payment/payLog/list", params);
        if (result == null || result.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, new cn.hutool.core.lang.TypeReference<java.util.Map<String, Object>>() {
        }, true);
    }


    public com.chinasofti.huateng.common.response.AlipayCommonResponse notifyCloseResult(String agreementCode, boolean result) {
        AlipayTripCloseResultReqDTO request = new AlipayTripCloseResultReqDTO();
        request.setAgreementNo(agreementCode);
        request.setResult(result);
        String response = postJsonAndGetResponse("/channel/notify/closeResultForAlipay", request);
        if (response == null || response.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(response);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : response;
        return JSONUtil.toBean(parseTarget, com.chinasofti.huateng.common.response.AlipayCommonResponse.class);
    }


    public AlipayProcessTerminationRespDTO processAlipayTermination(@RequestBody AlipayProcessTerminationReqDTO request) {
        return processAlipayTermination(request, java.util.Collections.emptyMap());
    }


    public AlipayProcessTerminationRespDTO processAlipayTermination(@RequestBody AlipayProcessTerminationReqDTO request,
                                                                   Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/alipay/termination/process", request, headers);
        return JSONUtil.toBean(result, new TypeReference<AlipayProcessTerminationRespDTO>() {
        }, true);
    }


    public com.chinasofti.huateng.common.response.AlipayCommonResponse executeTermination(String agreementCode) {
        String response = getAndGetResponse("/internal/alipay/termination/execute?agreementCode=" + agreementCode, new java.util.HashMap<>());
        if (response == null || response.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(response);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : response;
        return JSONUtil.toBean(parseTarget, com.chinasofti.huateng.common.response.AlipayCommonResponse.class);
    }


    public AlipayPayLogDTO selectByOrderNo(String orderNo) {
        String result = getAndGetResponse("/api/payment/payLog/detail?orderNo=" + orderNo, new java.util.HashMap<>());
        if (result == null || result.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, AlipayPayLogDTO.class);
    }


    public AlipayPayLogDTO selectByEntryId(String entryId) {
        String result = getAndGetResponse("/api/payment/payLog/entryId?entryId=" + entryId, new java.util.HashMap<>());
        if (result == null || result.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, AlipayPayLogDTO.class);
    }


    public AlipayPayLogDTO selectByExitId(String exitId) {
        String result = getAndGetResponse("/api/payment/payLog/exitId?exitId=" + exitId, new java.util.HashMap<>());
        if (result == null || result.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, AlipayPayLogDTO.class);
    }


    public AlipayPayLogDTO queryByTravelRecord(@RequestBody Map<String, String> params) {
        String result = postJsonAndGetResponse("/api/payment/payLog/queryByTravelRecord", params);
        if (result == null || result.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, AlipayPayLogDTO.class);
    }


    public Map<String, Object> selectAlipayPayLogListForTravel(String thirdUserId, String startDate, String endDate, int pageNum, int pageSize,
                                                               String debitRequestResult, String invoice) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("thirdUserId", thirdUserId);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        if (debitRequestResult != null) params.put("debitRequestResult", debitRequestResult);
        if (invoice != null) params.put("invoice", invoice);
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        String result = postJsonAndGetResponse("/api/payment/payLog/travelList", params);
        if (result == null || result.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, new cn.hutool.core.lang.TypeReference<Map<String, Object>>() {
        }, true);
    }


    public com.chinasofti.huateng.common.response.AlipayCommonResponse notifyBlackListChange(com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO request) {
        String result = postJsonAndGetResponse("/channel/notify/blackListChange", request);
        if (result == null || result.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, com.chinasofti.huateng.common.response.AlipayCommonResponse.class);
    }


    public com.chinasofti.huateng.common.response.AlipayCommonResponse compensateChannelSync(Map<String, String> headers) {
        String response = postJsonAndGetResponse("/internal/alipay/channelSync/compensate",
                java.util.Collections.emptyMap(), headers);
        if (response == null || response.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(response);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : response;
        return JSONUtil.toBean(parseTarget, com.chinasofti.huateng.common.response.AlipayCommonResponse.class);
    }


    public com.chinasofti.huateng.common.response.AlipayCommonResponse compensateRefundQuery(Map<String, String> headers) {
        String response = postJsonAndGetResponse("/internal/alipay/refund/compensateQuery",
                java.util.Collections.emptyMap(), headers);
        if (response == null || response.isEmpty()) {
            return null;
        }
        JSONObject wrapper = JSONUtil.parseObj(response);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : response;
        return JSONUtil.toBean(parseTarget, com.chinasofti.huateng.common.response.AlipayCommonResponse.class);
    }


    public java.util.List<AlipayTripPayTxnBriefDTO> queryPayTxnBrief(@RequestBody java.util.List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return java.util.List.of();
        }
        if (orderNos.size() > PAY_TXN_BRIEF_MAX_SIZE) {
            throw new IllegalArgumentException("批量补齐支付明细的订单号条数超限: " + orderNos.size()
                    + ", 上限 " + PAY_TXN_BRIEF_MAX_SIZE);
        }
        String result = postJsonAndGetResponse("/internal/alipay/payment/payTxnBrief", orderNos);
        if (result == null || result.isEmpty()) {
            return java.util.List.of();
        }
        return JSONUtil.toList(JSONUtil.parseArray(result), AlipayTripPayTxnBriefDTO.class);
    }


    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        String result = postJsonAndGetResponse("/internal/alipayPay/hasUnsettledOrderByCard", request);
        return JSONUtil.toBean(result, new TypeReference<CardUnsettledQueryRespDTO>() {
        }, true);
    }
}
