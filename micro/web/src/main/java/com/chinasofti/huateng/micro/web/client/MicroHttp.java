package com.chinasofti.huateng.micro.web.client;

import org.springframework.http.client.MultipartBodyBuilder;

import java.io.IOException;
import java.util.Map;

public interface MicroHttp {

    String postJsonAndGetResponse(String url, Object requestBody, Map<String, String> headers);

    String postJsonAndGetResponse(String url, Object requestBody) throws IOException;

    String getAndGetResponse(String url, Map<String, String> params, Map<String, String> headers);

    String getAndGetResponse(String url, Map<String, String> params);

    String postFormAndGetResponse(String url, Map<String, String> formData, Map<String, String> headers);

    String postFormAndGetResponse(String url, Map<String, String> formData);

    String postMultipartFormAndGetResponse(String url, MultipartBodyBuilder multipartBodyBuilder, Map<String, String> headers);
}
