package com.chinasofti.huateng.rpc.pay;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.RequestPayFailOrderReqDTO;
import com.chinasofti.huateng.model.app.RequestPayFailOrderResult;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.model.pay.OfflineCodeStatExportDTO;
import com.chinasofti.huateng.model.pay.OvertimeRefundableExportDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GateTxnPayClient extends ProxyWebClient {
    public GateTxnPayClient(@Value("${service.gateTxnPay.url:gate-txn-pay-service}") String baseUrl,
                            @Value("${service.gateTxnPay.openLogger:true}") boolean openLogger,
                            WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected boolean isNetworkRetryEnabled() {
        return false;
    }

    public GateTxnPayRespDTO requestGateTxnPay(@RequestBody GateTxnPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayRespDTO>() {
        }, true);
    }


    public GateTxnPayRespDTO queryOrderByBizKey(@RequestBody GateTxnPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/queryOrderByBizKey", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayRespDTO>() {
        }, true);
    }


    public GateTxnPayFailedOrderRespDTO hasFailedOrder(@RequestBody GateTxnPayFailedOrderReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/hasFailedOrder", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayFailedOrderRespDTO>() {
        }, true);
    }


    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/hasUnsettledOrderByCard", request);
        return JSONUtil.toBean(result, new TypeReference<CardUnsettledQueryRespDTO>() {
        }, true);
    }


    public GateTxnPayRespDTO syncDebitStatus(@RequestBody GateTxnPaySyncStatusReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/syncDebitStatus", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayRespDTO>() {
        }, true);
    }



    public List<GateTxnPayListDTO> requestTransList(@RequestBody QueryTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestTransList", request);
        return JSONUtil.toList(result, GateTxnPayListDTO.class);
    }


    public int countTransList(@RequestBody QueryTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/countTransList", request);
        return parseCount(result);
    }


    private static int parseCount(String rawBody) {
        if (rawBody == null) {
            throw new IllegalStateException("gate-txn-pay countTransList 返回 null，"
                    + "通常是对端不可达或网关未回体；MUST 先确认 service.gateTxnPay.url 与对端存活");
        }
        String trimmed = rawBody.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("gate-txn-pay countTransList 期望裸整数，实际响应体="
                    + (rawBody.length() > 200 ? rawBody.substring(0, 200) + "...(已截断)" : rawBody)
                    + "；对端契约可能已改（套了 CommonResult / 返了错误页），"
                    + "MUST 同批改本方法与 ticket-server、trans-query-server 两个调用点", e);
        }
    }



    public RequestTransStatisticsResult requestTransStatistics(@RequestBody RequestTransStatisticsReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestTransStatistics", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransStatisticsResult>() {
        }, true);
    }



    public GateTxnPayListDTO queryByOrderNo(String orderNo) {
        Map<String, String> request = Collections.singletonMap("orderNo", orderNo);
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/queryByOrderNo", request);
        return JSONUtil.toBean(result, GateTxnPayListDTO.class, true);
    }





    public RequestUserAccInfoResult requestUserAccInfo(@RequestBody RequestUserAccInfoReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestUserAccInfo", request);
        return JSONUtil.toBean(result, new TypeReference<RequestUserAccInfoResult>() {
        }, true);
    }



    public RequestPayFailOrderResult requestPayFailOrder(@RequestBody RequestPayFailOrderReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestPayFailOrder", request);
        return JSONUtil.toBean(result, new TypeReference<RequestPayFailOrderResult>() {
        }, true);
    }



    public CommonResult recoverOfflineFare(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/offline-fare/recover",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public CommonResult pushMetroTransfer(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/metro-transfer/push",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public CommonResult retryDefaultChannelDebits(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/debit/retry/default",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public CommonResult retryAlipayChannelDebits(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/debit/retry/alipay",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public CommonResult retryRecentUnpaidDebits(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/debit/retry/recent",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(
            @RequestBody GateTxnPayDebitConvergeReqDTO request) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/debit/converge", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayDebitConvergeRespDTO>() {
        }, true);
    }


    public CommonResult consumeRetryQueue(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/retry-queue/consume",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public CommonResult processOneSideOrders(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/oneside/process",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public List<OvertimeRefundableExportDTO> listOvertimeRefundable(String stationCode,
            String startDate, String endDate) {
        Map<String, String> params = new HashMap<>();
        putIfNotEmpty(params, "stationCode", stationCode);
        putIfNotEmpty(params, "startDate", startDate);
        putIfNotEmpty(params, "endDate", endDate);
        int pageSize = 1000;
        int pageNum = 1;
        List<OvertimeRefundableExportDTO> all = new ArrayList<>();
        while (true) {
            params.put("pageNum", String.valueOf(pageNum));
            params.put("pageSize", String.valueOf(pageSize));
            String result = getAndGetResponse("/page/gate-txn-pay/overtime-refundable", params, new HashMap<>());
            JSONObject data = JSONUtil.parseObj(result).getJSONObject("data");
            List<OvertimeRefundableExportDTO> page = data.getBeanList("list", OvertimeRefundableExportDTO.class);
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            pageNum++;
        }
        return all;
    }


    public List<OfflineCodeStatExportDTO> listOfflineCodeStats(String startDate, String endDate) {
        Map<String, String> params = new HashMap<>();
        putIfNotEmpty(params, "startDate", startDate);
        putIfNotEmpty(params, "endDate", endDate);
        String result = getAndGetResponse("/page/gate-txn-pay/offline-stats", params, new HashMap<>());
        JSONObject data = JSONUtil.parseObj(result).getJSONObject("data");
        return data.getBeanList("list", OfflineCodeStatExportDTO.class);
    }

    private void putIfNotEmpty(Map<String, String> params, String key, String value) {
        if (value != null && !value.isEmpty()) {
            params.put(key, value);
        }
    }
}
