package com.chinasofti.huateng.collectpay.utils;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;


@Component
@Slf4j
public class HttpUtils {

    @Autowired
    private RestTemplate restTemplate;

    public String doPost2(String url, Object accQueryStatusDto) {
        HttpClient httpClient = new HttpClient();
        httpClient.setTimeout(60000);
        PostMethod postMethod = new PostMethod(url);
        postMethod.addRequestHeader("accept", "*/*");
        postMethod.addRequestHeader("connection", "Keep-Alive");
        //设置json格式传送
        postMethod.addRequestHeader("Content-Type", "application/json;charset=UTF-8");
        //必须设置下面这个Header
        postMethod.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.81 Safari/537.36");

        //添加请求参数
        String json = JSONObject.toJSONString(accQueryStatusDto, SerializerFeature.WriteMapNullValue);
        log.info("请求地址 is {} 请求参数 json is {}",url, json);
        postMethod.setRequestBody(json);
        postMethod.getParams().setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "utf-8");

        String res = "";
        try {
            if(!ObjectUtils.isEmpty(postMethod.getParams())){
                log.info("发送请求 请求参数 is {}",JSONObject.toJSONString(accQueryStatusDto, SerializerFeature.WriteMapNullValue));
            }else {
                log.info("入参数 {}", postMethod.getParams());
            }
            int code = httpClient.executeMethod(postMethod);
            log.info("返回值 {}", code);
            if (code == 200) {
                res = postMethod.getResponseBodyAsString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    public String getToken(String url) {

        log.info("getToken is {}", url);
        ResponseEntity<JSONObject> exchange = restTemplate.exchange(url, HttpMethod.POST, null, JSONObject.class);
        JSONObject body = exchange.getBody();
        String access_token = null;
        if (!ObjectUtils.isEmpty(body.get("access_token"))) {
            access_token = body.get("access_token").toString();
            log.info("access_token is not null");
        } else {
            log.info("access_token is null");
        }
        return access_token;
    }

}