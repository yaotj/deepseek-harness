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

    /** 支付类客户端禁止网络层重试：重复提交/扣款风险不可接受。 */
    @Override
    protected boolean isNetworkRetryEnabled() {
        return false;
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

    // ==================== 每日批量退款（web-admin Quartz 触发） ====================

    /**
     * 需求 1 单程票购票未取票批量退款。
     *
     * @param headers 附加请求头（Quartz 侧传 traceparent）
     * @return {@code retCode=0000} 本轮跑完；{@code 9998} 上一轮仍在执行（限流，不是失败）
     */
    public CommonResult batchRefundSingleTicket(Map<String, String> headers) {
        return postBatchRefund("/internal/f2f/batch-refund/single-ticket", headers);
    }

    /** 需求 2 TVM 充值未到账批量退款，返回口径同 {@link #batchRefundSingleTicket}。 */
    public CommonResult batchRefundTopup(Map<String, String> headers) {
        return postBatchRefund("/internal/f2f/batch-refund/topup", headers);
    }

    /** 需求 3 BOM 非现金收款未履约批量退款，返回口径同 {@link #batchRefundSingleTicket}。 */
    public CommonResult batchRefundNoCash(Map<String, String> headers) {
        return postBatchRefund("/internal/f2f/batch-refund/no-cash", headers);
    }

    private CommonResult postBatchRefund(String url, Map<String, String> headers) {
        String result = postJsonAndGetResponse(url, new HashMap<>(),
                headers == null ? new HashMap<>() : headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 分页查询当面付订单（供异步导出使用）。
     */
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

