package com.chinasofti.huateng.gatetxnpay.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.QueryWalletTotalAmtReqDTO;
import com.chinasofti.huateng.model.app.QueryWalletTotalAmtResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.MultipartBodyBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/** 按 ITP-App 网关协议调用钱包累计金额接口，不是内部 RPC 调用。 */
@Service
public class WalletAppGatewayClient extends ProxyWebClient {
    private final String queryTotalAmtPath;
    private final String providerId;
    private final String charset;
    private final String format;
    private final String signType;

    public WalletAppGatewayClient(
            @Value("${wallet.app-gateway-url}") String gatewayUrl,
            @Value("${wallet.app-gateway-open-logger:false}") boolean openLogger,
            @Value("${wallet.app-query-total-amt-path:/ci/app/v2/queryTotalAmt}") String queryTotalAmtPath,
            @Value("${wallet.app-provider-id:01}") String providerId,
            @Value("${wallet.app-charset:utf-8}") String charset,
            @Value("${wallet.app-format:json}") String format,
            @Value("${wallet.app-sign-type:00}") String signType,
            WebClient.Builder webClientBuilder) {
        super(gatewayUrl, openLogger, webClientBuilder);
        this.queryTotalAmtPath = queryTotalAmtPath;
        this.providerId = providerId;
        this.charset = charset;
        this.format = format;
        this.signType = signType;
    }

    public QueryWalletTotalAmtResult queryTotalAmt(QueryWalletTotalAmtReqDTO bizData) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("providerId", providerId);
        form.put("charset", charset);
        form.put("format", format);
        form.put("timestamp", String.valueOf(System.currentTimeMillis()));
        form.put("signType", signType);
        form.put("bizData", JSON.toJSONString(bizData));
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        form.forEach(multipart::part);
        String result = postMultipartFormAndGetResponse(queryTotalAmtPath, multipart, null);
        return JSONUtil.toBean(result, new TypeReference<QueryWalletTotalAmtResult>() {}, true);
    }
}
