package com.chinasofti.huateng.rpc.para;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.RequestLineCodeListReqDTO;
import com.chinasofti.huateng.model.app.RequestLineCodeListResult;
import com.chinasofti.huateng.model.app.RequestBuySinlgeTicketMaxNumResult;
import com.chinasofti.huateng.model.app.RequestLineStationCodeVersionReqDTO;
import com.chinasofti.huateng.model.app.RequestLineStationCodeVersionResult;
import com.chinasofti.huateng.model.app.RequestStationCodeListReqDTO;
import com.chinasofti.huateng.model.app.RequestStationCodeListResult;
import com.chinasofti.huateng.model.app.RequestStationLineInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.model.app.RequestStationNameBatchReqDTO;
import com.chinasofti.huateng.model.app.RequestStationNameBatchResult;
import com.chinasofti.huateng.model.app.RequestStationNameReqDTO;
import com.chinasofti.huateng.model.app.RequestStationNameResult;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Map;

/**
 * para-server RPC 客户端。
 */
@Service
public class ParaClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(ParaClient.class);

    public ParaClient(@Value("${service.para.url:http://127.0.0.1:9107}") String baseUrl,
                      @Value("${service.para.openLogger:true}") boolean openLogger,
                      WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 调用 para-server FTP 参数文件扫描入库接口（web-server Quartz 定时任务用）。
     *
     * @return retCode=0000 表示本次扫描全部成功
     */
    public CommonResult quartzScanFtpPara() {
        return quartzScanFtpPara(Collections.emptyMap());
    }

    /**
     * 带 trace 头的重载，供 web-admin Quartz 任务调用，口径与 {@code PaySignClient} 的解约 / 通知补偿一致。
     *
     * <p>headers 由调用方组装成 W3C {@code traceparent}（末段采样标记固定 {@code 00}）加
     * {@code X-Vlogs-Capture}，**NEVER 传自定义 {@code traceId} 头**——下游 {@code FirstFilter}
     * 会把请求头 key 全部小写后塞 MDC，`traceId` 会变成 `traceid`，与 log4j2 的 {@code %X{traceId}}
     * 大小写不匹配，等于白传。</p>
     *
     * <p>⚠️ {@code ProxyWebClient} 不会自动注入任何 trace 头（它只从 MDC 取 authorization），
     * 所以这个重载是必需的，不能指望框架兜底。</p>
     */
    public CommonResult quartzScanFtpPara(Map<String, String> headers) {
        String path = "/para/import/ftp/quartz";
        log.info("调用para-server FTP参数扫描接口 path={}", path);
        String result = postJsonAndGetResponse(path, Collections.emptyMap(), headers);
        CommonResult response = JSONUtil.toBean(result, new TypeReference<CommonResult>() {}, true);
        log.info("调用para-server FTP参数扫描接口返回 path={}, retCode={}, retMsg={}", path,
                response == null ? null : response.getRetCode(), response == null ? null : response.getRetMsg());
        return response;
    }

    /**
     * IF8A-07 获取线路代码。
     *
     * <p>{@code RequestLineCodeListReqDTO} 是**零字段类**，直接 {@code bodyValue(request)}
     * 会被 WebClient 判定为无可用编码器并抛
     * {@code UnsupportedMediaTypeException: Content type 'application/json' not supported for bodyType=...}，
     * 请求根本发不出去、响应退化成全局异常处理器的 UUID retCode。因此这里与
     * {@link #requestBuySinlgeTicketMaxNum()} 一致，改送空 JSON 对象。
     * 入参保留在签名上只为兼容调用方，**NEVER 改回 {@code postJsonAndGetResponse(url, request)}**。</p>
     */
    public RequestLineCodeListResult requestLineCodeList(RequestLineCodeListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestLineCodeList", Collections.emptyMap());
        return JSONUtil.toBean(result, new TypeReference<RequestLineCodeListResult>() {}, true);
    }

    /**
     * IF8A-09 获取单次购买单程票最大张数。
     */
    public RequestBuySinlgeTicketMaxNumResult requestBuySinlgeTicketMaxNum() {
        String result = postJsonAndGetResponse("/ci/app/requestBuySinlgeTicketMaxNum", Collections.emptyMap());
        return JSONUtil.toBean(result, new TypeReference<RequestBuySinlgeTicketMaxNumResult>() {}, true);
    }

    /**
     * IF8A-08 获取车站代码。
     */
    public RequestStationCodeListResult requestStationCodeList(RequestStationCodeListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationCodeList", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationCodeListResult>() {}, true);
    }

    /**
     * IF8A-10 计算票价。
     */
    public RequestTicketPriceByStationResult requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTicketPriceByStation", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTicketPriceByStationResult>() {}, true);
    }

    /**
     * IF8A-17 获取线路站点代码版本。
     *
     * <p>与 {@link #requestLineCodeList(RequestLineCodeListReqDTO)} 同因：
     * {@code RequestLineStationCodeVersionReqDTO} 也是零字段类，**MUST** 送空 JSON 对象。</p>
     */
    public RequestLineStationCodeVersionResult requestLineStationCodeVersion(RequestLineStationCodeVersionReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestLineStationCodeVersion", Collections.emptyMap());
        return JSONUtil.toBean(result, new TypeReference<RequestLineStationCodeVersionResult>() {}, true);
    }

    /**
     * 根据车站代码查询车站名称。
     */
    public RequestStationNameResult requestStationName(RequestStationNameReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationName", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationNameResult>() {}, true);
    }

    /**
     * 查询车站线路信息。
     */
    public RequestStationLineInfoResult requestStationLineInfo(RequestStationLineInfoReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationLineInfo", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationLineInfoResult>() {}, true);
    }

    /**
     * 批量根据车站代码查询车站名称。
     */
    public RequestStationNameBatchResult requestStationNameBatch(RequestStationNameBatchReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationNameBatch", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationNameBatchResult>() {}, true);
    }
}
