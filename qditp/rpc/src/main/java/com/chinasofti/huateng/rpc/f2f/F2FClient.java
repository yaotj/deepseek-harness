package com.chinasofti.huateng.rpc.f2f;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.RequestApplicationResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Map;

@Service
public class F2FClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(AccountClient.class);

    public F2FClient(@Value("${service.f2f.url:http://127.0.0.1:9098}") String baseUrl, @Value("${service.f2f.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    public RequestApplicationResult testtbNoticeAppTask() {
        return testtbNoticeAppTask(Collections.emptyMap());
    }


    public RequestApplicationResult noticeTakeTicketTask() {
        return noticeTakeTicketTask(Collections.emptyMap());
    }


    public RequestApplicationResult noticeTakeTicketFailureTask() {
        return noticeTakeTicketFailureTask(Collections.emptyMap());
    }


    public RequestApplicationResult noticeRefundTask() {
        return noticeRefundTask(Collections.emptyMap());
    }


    public RequestApplicationResult testtbNoticeAppTask(Map<String, String> headers) {
        return post("pay/noticeAppTask/testtbNoticeAppTask", headers);
    }


    public RequestApplicationResult noticeTakeTicketTask(Map<String, String> headers) {
        return post("pay/noticeAppTask/noticeTakeTicketTask", headers);
    }


    public RequestApplicationResult noticeTakeTicketFailureTask(Map<String, String> headers) {
        return post("pay/noticeAppTask/noticeTakeTicketFailureTask", headers);
    }


    public RequestApplicationResult noticeRefundTask(Map<String, String> headers) {
        return post("pay/noticeAppTask/noticeRefundTask", headers);
    }


    private RequestApplicationResult post(String path, Map<String, String> headers) {
        String result = postJsonAndGetResponse(path, null, headers);
        return JSONUtil.toBean(result, new TypeReference<RequestApplicationResult>() {
        }, true);
    }
}
