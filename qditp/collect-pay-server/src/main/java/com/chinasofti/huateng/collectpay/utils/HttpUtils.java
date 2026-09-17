package com.chinasofti.huateng.collectpay.utils;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.chinasofti.huateng.model.app.ItpCommonRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
import java.util.concurrent.TimeUnit;


@Component
@Slf4j
public class HttpUtils {

    private static final OkHttpClient FORM_DATA_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Autowired
    private RestTemplate restTemplate;

    public String doPost2(String url, Object request) {
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
        String json = JSONObject.toJSONString(request, SerializerFeature.WriteMapNullValue);
        log.info("请求地址 is {} 请求参数 json is {}",url, json);
        postMethod.setRequestBody(json);
        postMethod.getParams().setParameter(HttpMethodParams.HTTP_CONTENT_CHARSET, "utf-8");

        String res = "";
        try {
            if(!ObjectUtils.isEmpty(postMethod.getParams())){
                log.info("发送请求 请求参数 is {}",JSONObject.toJSONString(request, SerializerFeature.WriteMapNullValue));
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

    /** 按 APP 接口协议发送 multipart/form-data 报文，业务字段以 JSON 字符串放入 bizData。 */
    public String doPostFormData(String url, ItpCommonRequest<?> request) {
        String bizData = JSONObject.toJSONString(request.getBizData(), SerializerFeature.WriteMapNullValue);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("providerId", valueOrEmpty(request.getProviderId()))
                .addFormDataPart("charset", valueOrEmpty(request.getCharset()))
                .addFormDataPart("format", valueOrEmpty(request.getFormat()))
                .addFormDataPart("timestamp", valueOrEmpty(request.getTimestamp()))
                .addFormDataPart("deviceId", valueOrEmpty(request.getDeviceId()))
                .addFormDataPart("signType", valueOrEmpty(request.getSignType()))
                .addFormDataPart("sign", valueOrEmpty(request.getSign()))
                .addFormDataPart("bizData", bizData)
                .build();

        Request httpRequest = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        log.info("发送 APP form-data 请求, url={}, providerId={}, bizData={}",
                url, request.getProviderId(), bizData);
        try (Response response = FORM_DATA_HTTP_CLIENT.newCall(httpRequest).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            log.info("APP form-data 请求完成, httpCode={}, response={}", response.code(), responseBody);
            return responseBody;
        } catch (IOException e) {
            log.error("发送 APP form-data 请求异常, url={}", url, e);
            return "";
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
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
