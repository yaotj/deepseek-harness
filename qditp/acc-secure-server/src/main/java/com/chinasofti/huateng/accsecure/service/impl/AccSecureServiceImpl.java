package com.chinasofti.huateng.accsecure.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.accsecure.config.AccSecureProperties;
import com.chinasofti.huateng.accsecure.constant.AccSecureErrorCodeEnum;
import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;
import com.chinasofti.huateng.model.app.ItpCommonRequest;
import com.chinasofti.huateng.accsecure.model.request.RequestCaKeyReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestDpkReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestExportUserPriKeyReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestHecCardDateReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestQrLogicNumListReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestSignInsDataReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestSignPubkeyReqDTO;
import com.chinasofti.huateng.accsecure.model.request.RequestUserSm2KeyReqDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestCaKeyRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestDpkRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestExportUserPriKeyRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestHecCardDateRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestQrLogicNumListRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestSignInsDataRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestSignPubkeyRespDTO;
import com.chinasofti.huateng.accsecure.model.response.RequestUserSm2KeyRespDTO;
import com.chinasofti.huateng.accsecure.service.AccSecureService;
import com.chinasofti.huateng.accsecure.util.AccSecureSignUtils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class AccSecureServiceImpl implements AccSecureService {
    private static final Logger log = LoggerFactory.getLogger(AccSecureServiceImpl.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient okHttpClient;
    private final AccSecureProperties properties;


    public AccSecureServiceImpl(AccSecureProperties properties) {
        this.properties = properties;
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }


    @Override
    public RequestQrLogicNumListRespDTO requestQrLogicNumList(RequestQrLogicNumListReqDTO request) {
        return post(properties.getPaths().getRequestQrLogicNumList(), request, RequestQrLogicNumListRespDTO.class);
    }


    @Override
    public RequestCaKeyRespDTO requestCaKey(RequestCaKeyReqDTO request) {
        return post(properties.getPaths().getRequestCaKey(), request, RequestCaKeyRespDTO.class);
    }


    @Override
    public RequestUserSm2KeyRespDTO requestUserSm2Key(RequestUserSm2KeyReqDTO request) {
        return post(properties.getPaths().getRequestUserSm2Key(), request, RequestUserSm2KeyRespDTO.class);
    }


    @Override
    public RequestSignPubkeyRespDTO requestSignPubkey(RequestSignPubkeyReqDTO request) {
        return post(properties.getPaths().getRequestSignPubkey(), request, RequestSignPubkeyRespDTO.class);
    }


    @Override
    public RequestExportUserPriKeyRespDTO requestExportUserPriKey(RequestExportUserPriKeyReqDTO request) {
        return post(properties.getPaths().getRequestExportUserPriKey(), request, RequestExportUserPriKeyRespDTO.class);
    }


    @Override
    public RequestSignInsDataRespDTO requestSignInsData(RequestSignInsDataReqDTO request) {
        return post(properties.getPaths().getRequestSignInsData(), request, RequestSignInsDataRespDTO.class);
    }


    @Override
    public RequestDpkRespDTO requestDpk(RequestDpkReqDTO request) {
        return post(properties.getPaths().getRequestDpk(), request, RequestDpkRespDTO.class);
    }


    @Override
    public RequestHecCardDateRespDTO requestHecCardDate(RequestHecCardDateReqDTO request) {
        return post(properties.getPaths().getRequestHecCardDate(), request, RequestHecCardDateRespDTO.class);
    }


    private <TReq, TResp extends AccBizBaseResponse> TResp post(String path, TReq bizData, Class<TResp> responseClass) {
        try {
            String url = buildUrl(path);
            ItpCommonRequest<TReq> request = buildAccRequest(bizData);
            String requestJson = JSON.toJSONString(request);
            log.info("调用ACC安全接口, url={}, request={}", url, requestJson);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestJson, JSON_MEDIA_TYPE))
                    .build();
            try (Response response = okHttpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return buildErrorResponse(responseClass, AccSecureErrorCodeEnum.ACC_CALL_FAIL, "ACC接口调用失败");
                }
                String responseJson = response.body().string();
                log.info("ACC安全接口返回, url={}, response={}", url, responseJson);
                return parseResponse(responseJson, responseClass);
            }
        } catch (Exception e) {
            log.error("调用ACC安全接口异常, path={}", path, e);
            return buildErrorResponse(responseClass, AccSecureErrorCodeEnum.SYSTEM_ERROR, AccSecureErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
    }


    private <TReq> ItpCommonRequest<TReq> buildAccRequest(TReq bizData) {
        ItpCommonRequest<TReq> request = new ItpCommonRequest<>();
        request.setProviderId(properties.getProviderId());
        request.setCharset(properties.getCharset());
        request.setFormat(properties.getFormat());
        request.setTimestamp(AccSecureSignUtils.buildTimestamp());
        request.setDeviceId(properties.getDeviceId());
        request.setSignType(properties.getSignType());
        request.setBizData(bizData);
        request.setSign(AccSecureSignUtils.buildSign(
                request.getProviderId(),
                request.getCharset(),
                request.getFormat(),
                request.getTimestamp(),
                request.getDeviceId(),
                request.getSignType(),
                request.getBizData(),
                properties));
        return request;
    }


    private String buildUrl(String path) {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalArgumentException("acc.secure.base-url未配置");
        }
        String baseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        String route = path.startsWith("/") ? path : "/" + path;
        return baseUrl + route;
    }


    private <TResp extends AccBizBaseResponse> TResp parseResponse(String responseJson, Class<TResp> responseClass) {
        JSONObject root = JSON.parseObject(responseJson);
        if (root == null) {
            return buildErrorResponse(responseClass, AccSecureErrorCodeEnum.SYSTEM_ERROR, "ACC返回报文为空");
        }
        if (root.containsKey("bizData")) {
            JSONObject bizData = root.getJSONObject("bizData");
            TResp response = JSON.toJavaObject(bizData, responseClass);
            return normalizeBaseFields(response, bizData, root);
        }
        TResp response = JSON.toJavaObject(root, responseClass);
        return normalizeBaseFields(response, root, root);
    }


    private <TResp extends AccBizBaseResponse> TResp normalizeBaseFields(TResp response, JSONObject bizRoot, JSONObject fullRoot) {
        if (response == null) {
            throw new IllegalStateException("ACC响应解析失败");
        }
        if (!StringUtils.hasText(response.getRetCode())) {
            if (bizRoot.containsKey("retCode")) {
                response.setRetCode(bizRoot.getString("retCode"));
            } else if (fullRoot.containsKey("retCode")) {
                response.setRetCode(fullRoot.getString("retCode"));
            }
        }
        if (!StringUtils.hasText(response.getRetMsg())) {
            if (bizRoot.containsKey("retMsg")) {
                response.setRetMsg(bizRoot.getString("retMsg"));
            } else if (fullRoot.containsKey("retMsg")) {
                response.setRetMsg(fullRoot.getString("retMsg"));
            }
        }
        return response;
    }


    private <TResp extends AccBizBaseResponse> TResp buildErrorResponse(Class<TResp> responseClass,
                                                                        AccSecureErrorCodeEnum errorCode,
                                                                        String message) {
        try {
            TResp response = responseClass.getDeclaredConstructor().newInstance();
            response.setRetCode(errorCode.getCode());
            response.setRetMsg(message);
            return response;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("创建响应对象失败", e);
        }
    }
}
