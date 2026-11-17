package com.chinasofti.huateng.rpc.facepay;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.pay.FacePayOrderExportDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class FacePayClient extends ProxyWebClient {

    public FacePayClient(@Value("${service.facePay.url:face-pay-service}") String baseUrl,
                         @Value("${service.facePay.openLogger:true}") boolean openLogger,
                         WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected boolean isNetworkRetryEnabled() {
        return false;
    }



    public SupplementOrderRespDTO requestPayOrder(@RequestBody SupplementOrderReqDTO request) {
        String result = postJsonAndGetResponse("/ci/facePay/app/requestPayOrder", request);
        return JSONUtil.toBean(result, new TypeReference<SupplementOrderRespDTO>() {
        }, true);
    }



    public CommonResult batchRefundSingleTicket(Map<String, String> headers) {
        return postBatchRefund("/internal/f2f/batch-refund/single-ticket", headers);
    }

    public CommonResult batchRefundTopup(Map<String, String> headers) {
        return postBatchRefund("/internal/f2f/batch-refund/topup", headers);
    }

    public CommonResult batchRefundNoCash(Map<String, String> headers) {
        return postBatchRefund("/internal/f2f/batch-refund/no-cash", headers);
    }

    private CommonResult postBatchRefund(String url, Map<String, String> headers) {
        String result = postJsonAndGetResponse(url, new HashMap<>(),
                headers == null ? new HashMap<>() : headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }


    public List<FacePayOrderExportDTO> listFacePayOrders(String orderNo, String payCenterOrderNo,
            String payCenterChannelOrderNo, String deviceId, String channel, String bizType,
            String orderStatus, String beginTime, String endTime) {
        Map<String, String> params = new HashMap<>();
        putIfNotEmpty(params, "orderNo", orderNo);
        putIfNotEmpty(params, "payCenterOrderNo", payCenterOrderNo);
        putIfNotEmpty(params, "payCenterChannelOrderNo", payCenterChannelOrderNo);
        putIfNotEmpty(params, "deviceId", deviceId);
        putIfNotEmpty(params, "channel", channel);
        putIfNotEmpty(params, "bizType", bizType);
        putIfNotEmpty(params, "orderStatus", orderStatus);
        putIfNotEmpty(params, "beginTime", beginTime);
        putIfNotEmpty(params, "endTime", endTime);
        int pageSize = 1000;
        int pageNum = 1;
        List<FacePayOrderExportDTO> all = new ArrayList<>();
        while (true) {
            params.put("pageNum", String.valueOf(pageNum));
            params.put("pageSize", String.valueOf(pageSize));
            String result = getAndGetResponse("/page/face-pay/orders", params, new HashMap<>());
            JSONObject data = JSONUtil.parseObj(result).getJSONObject("data");
            List<FacePayOrderExportDTO> page = data.getBeanList("list", FacePayOrderExportDTO.class);
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

    private void putIfNotEmpty(Map<String, String> params, String key, String value) {
        if (value != null && !value.isEmpty()) {
            params.put(key, value);
        }
    }
}

