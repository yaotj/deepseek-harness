package com.chinasofti.huateng.rpc.pay;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Collections;
import java.util.Map;

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

    /**
     * 按交易业务键查询 GT 订单号（供 ticket-server 关联查询 tradeOrderNo）。
     */
    public GateTxnPayRespDTO queryOrderByBizKey(@RequestBody GateTxnPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/queryOrderByBizKey", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayRespDTO>() {
        }, true);
    }

    /**
     * 查询用户指定支付渠道在指定时间之后是否存在扣费失败订单（供 pay-sign-server 解约流程调用）。
     */
    public GateTxnPayFailedOrderRespDTO hasFailedOrder(@RequestBody GateTxnPayFailedOrderReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/hasFailedOrder", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayFailedOrderRespDTO>() {
        }, true);
    }

    // ==================== IF8A-05 APP 交易记录列表 RPC ====================

    /**
     * 分页查询进出站交易记录（供 ticket-server 调用）。
     */
    public List<GateTxnPayListDTO> requestTransList(@RequestBody QueryTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestTransList", request);
        return JSONUtil.toList(result, GateTxnPayListDTO.class);
    }

    /**
     * 统计 IF8A-05 分页查询结果总数（供 ticket-server 调用）。
     */
    public int countTransList(@RequestBody QueryTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/countTransList", request);
        return Integer.parseInt(result.trim());
    }

    // ==================== IF8A-34 APP 订单详情 RPC ====================

    /**
     * 按订单号查询交易记录（供 ticket-server 调用）。
     */
    public GateTxnPayListDTO queryByOrderNo(String orderNo) {
        Map<String, String> request = Collections.singletonMap("orderNo", orderNo);
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/queryByOrderNo", request);
        return JSONUtil.toBean(result, GateTxnPayListDTO.class, true);
    }
}
