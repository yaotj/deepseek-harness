package com.chinasofti.huateng.cardpool.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.cardpool.config.AccSecureProperties;
import com.chinasofti.huateng.cardpool.util.AccSignUtils;
import com.chinasofti.huateng.model.accsecure.RequestQrLogicNumListReqDTO;
import com.chinasofti.huateng.model.accsecure.RequestQrLogicNumListRespDTO;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * ACC 逻辑卡号接口（IF7B-01）适配器。
 *
 * <p>由 {@code acc-secure-server} 迁入本模块直连 ACC，去掉一跳内网转发：报文外壳、签名、
 * HTTP 通信都收敛在本类，领域服务只传业务参数、只看 {@code retCode} 与 {@code fileName}。</p>
 *
 * <p><b>报文形态以 2026-09-09 对 ACC 测试环境的实测为准</b>，与迁出侧不同：
 * 迁出侧 {@code AccSecureServiceImpl} 发 {@code application/json}（整体 JSON、bizData 为对象），
 * 实测 ACC 一律回 {@code 1003 解析请求数据失败}；ACC 只接受
 * {@code application/x-www-form-urlencoded}，公共参数平铺为表单字段、{@code bizData} 为
 * **JSON 字符串**，即 AGENTS.md §4 记载的项目统一形态。迁出侧因始终未接线，该缺陷从未暴露。</p>
 *
 * <p>响应字段实测为 {@code fileName} / {@code retCode} / <b>{@code returnMsg}</b>——
 * 消息键是 {@code returnMsg} 而非规格写的 {@code RetMsg}，解析时两者都兼容。</p>
 *
 * <p>与迁出前的另一处行为差异：传输层失败**抛异常**而不是返回带内部错误码的壳对象，
 * 这样批次表 {@code ERROR_MSG} 能落到真实原因而非统一的 8007/9001。</p>
 */
@Component
public class AccLogicNumClient {

    private static final Logger log = LoggerFactory.getLogger(AccLogicNumClient.class);

    private final AccSecureProperties properties;
    private final OkHttpClient httpClient;

    /**
     * 构造 ACC 客户端。
     *
     * @param properties ACC 调用配置
     */
    public AccLogicNumClient(AccSecureProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 调用 IF7B-01 请求逻辑卡号文件。
     *
     * @param request 请求数量、请求流水号与 ACC 票种
     * @return ACC 返回码与逻辑卡号文件名
     * @throws IllegalStateException 地址未配置、HTTP 失败或响应不可解析
     */
    public RequestQrLogicNumListRespDTO requestQrLogicNumList(RequestQrLogicNumListReqDTO request) {
        String url = buildUrl(properties.getRequestQrLogicNumListPath());
        log.info("调用ACC逻辑卡号接口, url={}, requestSeq={}, ticketType={}, requestNum={}",
                url, request.getRequestSeq(), request.getTicketType(), request.getRequestNum());
        Request httpRequest = new Request.Builder()
                .url(url)
                .post(buildFormBody(request))
                .build();
        String responseJson;
        int httpStatus;
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            httpStatus = response.code();
            responseJson = response.body() == null ? null : response.body().string();
        } catch (Exception ex) {
            throw new IllegalStateException("ACC接口通信异常, url=" + url + ", cause=" + ex.getMessage(), ex);
        }
        log.info("ACC逻辑卡号接口返回, url={}, httpStatus={}, response={}", url, httpStatus, responseJson);
        if (!StringUtils.hasText(responseJson)) {
            throw new IllegalStateException("ACC返回报文为空, url=" + url + ", httpStatus=" + httpStatus);
        }
        return parseResponse(responseJson, url);
    }

    /**
     * 组装 ACC 表单报文：公共参数平铺，{@code bizData} 为 JSON 字符串。
     *
     * @param bizData 业务参数
     * @return 表单请求体
     */
    private FormBody buildFormBody(RequestQrLogicNumListReqDTO bizData) {
        String timestamp = AccSignUtils.buildTimestamp();
        String signType = properties.getSignType();
        String sign = AccSignUtils.buildSign(
                properties.getProviderId(),
                properties.getCharset(),
                properties.getFormat(),
                timestamp,
                properties.getDeviceId(),
                signType,
                bizData,
                properties);
        return new FormBody.Builder()
                .add("providerId", nullToEmpty(properties.getProviderId()))
                .add("charset", nullToEmpty(properties.getCharset()))
                .add("format", nullToEmpty(properties.getFormat()))
                .add("timestamp", timestamp)
                .add("deviceId", nullToEmpty(properties.getDeviceId()))
                .add("signType", nullToEmpty(signType))
                .add("sign", nullToEmpty(sign))
                .add("bizData", JSON.toJSONString(bizData))
                .build();
    }

    /**
     * {@link FormBody.Builder#add} 不接受 null，统一兜成空串。
     *
     * @param value 原值
     * @return 非 null 值
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 拼接 ACC 调用地址。
     *
     * @param path 接口路径
     * @return 完整 URL
     */
    private String buildUrl(String path) {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("acc.secure.base-url未配置");
        }
        String baseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        String route = path.startsWith("/") ? path : "/" + path;
        return baseUrl + route;
    }

    /**
     * 解析 ACC 返回报文，兼容业务字段直接在外层与被 {@code bizData} 包一层两种结构。
     *
     * <p>{@code bizData} 为 JSON null 时按「未包裹」处理，避免把字面量当对象再解析一次。</p>
     *
     * @param responseJson 原始响应体
     * @param url          调用地址，仅用于异常信息
     * @return 业务响应
     */
    private RequestQrLogicNumListRespDTO parseResponse(String responseJson, String url) {
        if (!StringUtils.hasText(responseJson)) {
            throw new IllegalStateException("ACC返回报文为空, url=" + url);
        }
        JSONObject root;
        try {
            root = JSON.parseObject(responseJson);
        } catch (Exception ex) {
            throw new IllegalStateException("ACC返回报文不是JSON对象, url=" + url, ex);
        }
        if (root == null) {
            throw new IllegalStateException("ACC返回报文为空, url=" + url);
        }
        JSONObject bizRoot = root.getJSONObject("bizData");
        if (bizRoot == null) {
            bizRoot = root;
        }
        RequestQrLogicNumListRespDTO response = bizRoot.toJavaObject(RequestQrLogicNumListRespDTO.class);
        if (response == null) {
            throw new IllegalStateException("ACC响应解析失败, url=" + url);
        }
        normalizeBaseFields(response, bizRoot, root);
        return response;
    }

    /**
     * 兜底补齐 {@code retCode} / {@code retMsg}，字段可能出现在外层而不在 bizData 内。
     *
     * <p>消息字段有两种键名：规格写 {@code RetMsg}，ACC 测试环境实测返回 {@code returnMsg}，
     * 这里按 {@code retMsg} → {@code returnMsg} 顺序取，两种都能落到 {@code ERROR_MSG}。</p>
     *
     * @param response 业务响应
     * @param bizRoot  业务字段所在节点
     * @param fullRoot 报文根节点
     */
    private void normalizeBaseFields(RequestQrLogicNumListRespDTO response, JSONObject bizRoot, JSONObject fullRoot) {
        if (!StringUtils.hasText(response.getRetCode())) {
            response.setRetCode(firstText(bizRoot, fullRoot, "retCode"));
        }
        if (!StringUtils.hasText(response.getRetMsg())) {
            String message = firstText(bizRoot, fullRoot, "retMsg");
            if (!StringUtils.hasText(message)) {
                message = firstText(bizRoot, fullRoot, "returnMsg");
            }
            response.setRetMsg(message);
        }
    }

    /**
     * 先取业务节点、再取根节点的字符串字段。
     *
     * @param bizRoot  业务节点
     * @param fullRoot 根节点
     * @param key      字段名
     * @return 字段值，都没有时返回 {@code null}
     */
    private String firstText(JSONObject bizRoot, JSONObject fullRoot, String key) {
        String value = bizRoot.getString(key);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return fullRoot.getString(key);
    }
}
