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
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundRespDTO;
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

/**
 * 支付宝出行-签约服务 RPC 客户端。
 */
@Service
public class AlipayPaySignClient extends ProxyWebClient {

    public AlipayPaySignClient(@Value("${service.alipay-pay-sign.url:http://alipay-pay-sign-server:8080}") String baseUrl,
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
     *
     * <p>2026-09-18：路径由 {@code /api/payment/payQuery} 迁至 {@code /internal/alipay/payment/payQuery}
     * （该端点只有本方法一个调用方、属内部接口）。<b>旧路径已在服务端删除、没有别名</b>，
     * 因此本文件所在的 {@code fep-alipay} 与 {@code alipay-pay-sign-server} 两个镜像 MUST 同批滚更。
     */
    public AlipayTripPayQueryRespDTO alipayTripPayQuery(@RequestBody AlipayTripPayQueryReqDTO request) {
        String result = postJsonAndGetResponse("/internal/alipay/payment/payQuery", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripPayQueryRespDTO>() {
        }, true);
    }

    /**
     * 支付宝出行-退款申请。
     *
     * <p>2026-09-18：路径由 {@code /api/payment/requestRefund} 迁至
     * {@code /internal/alipay/payment/requestRefund}（该端点只有本方法一个调用方、真实入口是运维侧人工退款）。
     * <b>旧路径已在服务端删除、没有别名</b>，因此 {@code fep-alipay} 与 {@code alipay-pay-sign-server}
     * 两个镜像 MUST 同批滚更。
     */
    public AlipayTripRequestRefundRespDTO alipayTripRequestRefund(@RequestBody AlipayTripRequestRefundReqDTO request) {
        String result = postJsonAndGetResponse("/internal/alipay/payment/requestRefund", request);
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
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
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
        JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof JSONObject) ? ((JSONObject) data).toString() : result;
        return JSONUtil.toBean(parseTarget, com.chinasofti.huateng.common.response.AlipayCommonResponse.class);
    }

    /**
     * 支付宝出行-查询订单列表。
     */
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

    /**
     * 支付宝出行-业务关闭结果通知。
     */
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

    /**
     * 支付宝出行-销卡批处理。
     */
    public AlipayProcessTerminationRespDTO processAlipayTermination(@RequestBody AlipayProcessTerminationReqDTO request) {
        return processAlipayTermination(request, java.util.Collections.emptyMap());
    }

    /**
     * 支付宝出行-销卡批处理（带 trace 头）。
     */
    public AlipayProcessTerminationRespDTO processAlipayTermination(@RequestBody AlipayProcessTerminationReqDTO request,
                                                                   Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/alipay/termination/process", request, headers);
        return JSONUtil.toBean(result, new TypeReference<AlipayProcessTerminationRespDTO>() {
        }, true);
    }

    /**
     * 执行解约。
     *
     * <p>2026-09-18：路径由 {@code /channel/executeTermination} 迁至
     * {@code /internal/alipay/termination/execute}（该端点只有本方法一个调用方、属内部接口，
     * 且与同前缀的 {@code /process} 走同一套销卡语义）。<b>旧路径已在服务端删除、没有别名</b>，
     * 因此 {@code fep-alipay} 与 {@code alipay-pay-sign-server} 两个镜像 MUST 同批滚更。
     *
     * <p><b>HTTP 形态刻意保持 GET + query 串拼接不变</b>：改成 POST 属另一件事，
     * 不与「迁 internal」混做。
     */
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

    /**
     * 支付宝出行-按订单号查询支付日志。
     */
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

    /**
     * 支付宝出行-按进站交易ID查询支付流水。
     */
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

    /**
     * 按出站交易ID查询支付流水。
     */
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

    /**
     * 支付宝出行-按乘车记录查询支付流水。
     */
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

    /**
     * 支付宝出行-查询乘车记录支付流水列表。
     */
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

    /**
     * 通知支付宝黑名单变更。
     */
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

    /**
     * 支付宝出行-支付通道同步补偿（ADR-D132）。
     *
     * <p>无入参：批量大小与重试上限都是 alipay-pay-sign-server 侧的配置项。
     * 这里传空 body 只为满足 POST 形态，**NEVER 改成让调用方传批量大小** ——
     * 那等于把「一次扫多少」交给 Quartz 配置页面。</p>
     *
     * <p>由 web-admin 的 `alipayChannelSyncQuartzTask` 带 trace 头打进来；
     * 该端点是本模块补偿的**唯一驱动源**，判断它有没有在跑 MUST 查 `SYS_JOB_LOG`。</p>
     */
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

    /**
     * 按卡号查询支付宝出行链路是否仍有未结清订单（供 blacklist-server 盘点黑名单可解除性调用）。
     */
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        String result = postJsonAndGetResponse("/internal/alipayPay/hasUnsettledOrderByCard", request);
        return JSONUtil.toBean(result, new TypeReference<CardUnsettledQueryRespDTO>() {
        }, true);
    }
}
