package com.chinasofti.huateng.accsecure.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.accsecure.config.AccSecureProperties;
import com.chinasofti.huateng.accsecure.constant.AccSecureErrorCodeEnum;
import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;
import com.chinasofti.huateng.accsecure.model.common.AccCommonRequest;
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

    /**
     * 初始化 HTTP 客户端和 ACC 配置。
     */
    public AccSecureServiceImpl(AccSecureProperties properties) {
        this.properties = properties;
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 调用 IF7B-01，请求逻辑卡号列表。
     */
    @Override
    public RequestQrLogicNumListRespDTO requestQrLogicNumList(RequestQrLogicNumListReqDTO request) {
        return post(properties.getPaths().getRequestQrLogicNumList(), request, RequestQrLogicNumListRespDTO.class);
    }

    /**
     * 调用 IF7B-02，请求生成地铁 CA 密钥。
     */
    @Override
    public RequestCaKeyRespDTO requestCaKey(RequestCaKeyReqDTO request) {
        return post(properties.getPaths().getRequestCaKey(), request, RequestCaKeyRespDTO.class);
    }

    /**
     * 调用 IF7B-03，请求生成用户 SM2 密钥。
     */
    @Override
    public RequestUserSm2KeyRespDTO requestUserSm2Key(RequestUserSm2KeyReqDTO request) {
        return post(properties.getPaths().getRequestUserSm2Key(), request, RequestUserSm2KeyRespDTO.class);
    }

    /**
     * 调用 IF7B-04，请求签名用户公钥。
     */
    @Override
    public RequestSignPubkeyRespDTO requestSignPubkey(RequestSignPubkeyReqDTO request) {
        return post(properties.getPaths().getRequestSignPubkey(), request, RequestSignPubkeyRespDTO.class);
    }

    /**
     * 调用 IF7B-05，请求导出用户私钥。
     */
    @Override
    public RequestExportUserPriKeyRespDTO requestExportUserPriKey(RequestExportUserPriKeyReqDTO request) {
        return post(properties.getPaths().getRequestExportUserPriKey(), request, RequestExportUserPriKeyRespDTO.class);
    }

    /**
     * 调用 IF7B-06，请求签名行业数据。
     */
    @Override
    public RequestSignInsDataRespDTO requestSignInsData(RequestSignInsDataReqDTO request) {
        return post(properties.getPaths().getRequestSignInsData(), request, RequestSignInsDataRespDTO.class);
    }

    /**
     * 调用 IF7B-07，请求 HCE 卡片消费密钥。
     */
    @Override
    public RequestDpkRespDTO requestDpk(RequestDpkReqDTO request) {
        return post(properties.getPaths().getRequestDpk(), request, RequestDpkRespDTO.class);
    }

    /**
     * 调用 IF7B-08，请求发售 HCE 单程票数据。
     */
    @Override
    public RequestHecCardDateRespDTO requestHecCardDate(RequestHecCardDateReqDTO request) {
        return post(properties.getPaths().getRequestHecCardDate(), request, RequestHecCardDateRespDTO.class);
    }

    /**
     * 封装 ACC 公共报文后，通过 HTTP POST 调用指定 ACC 接口，并解析返回的业务报文。
     */
    private <TReq, TResp extends AccBizBaseResponse> TResp post(String path, TReq bizData, Class<TResp> responseClass) {
        try {
            String url = buildUrl(path);
            AccCommonRequest<TReq> request = buildAccRequest(bizData);
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

    /**
     * 按文档要求组装 ACC 请求公共参数和签名值。
     */
    private <TReq> AccCommonRequest<TReq> buildAccRequest(TReq bizData) {
        AccCommonRequest<TReq> request = new AccCommonRequest<>();
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

    /**
     * 把配置中的 ACC 根地址和接口路径拼成最终调用 URL。
     */
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

    /**
     * 解析 ACC 返回报文，兼容“外层直接返回业务字段”和“bizData 包裹业务字段”两种结构。
     */
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

    /**
     * 兜底补齐业务响应中的 retCode 和 retMsg，避免字段位于不同层级时丢失。
     */
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

    /**
     * 在调用失败或解析异常时，构造统一的错误响应对象返回给上层。
     */
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
