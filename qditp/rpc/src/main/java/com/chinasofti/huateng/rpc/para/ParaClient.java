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
import com.chinasofti.huateng.model.para.RiskRuleBlacklistSyncRespDTO;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Map;


@Service
public class ParaClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(ParaClient.class);

    public ParaClient(@Value("${service.para.url:http://127.0.0.1:9107}") String baseUrl,
                      @Value("${service.para.openLogger:true}") boolean openLogger,
                      WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }


    public CommonResult quartzScanFtpPara() {
        return quartzScanFtpPara(Collections.emptyMap());
    }


    public CommonResult quartzScanFtpPara(Map<String, String> headers) {
        String path = "/para/import/ftp/quartz";
        log.info("调用para-server FTP参数扫描接口 path={}", path);
        String result = postJsonAndGetResponse(path, Collections.emptyMap(), headers);
        CommonResult response = JSONUtil.toBean(result, new TypeReference<CommonResult>() {}, true);
        log.info("调用para-server FTP参数扫描接口返回 path={}, retCode={}, retMsg={}", path,
                response == null ? null : response.getRetCode(), response == null ? null : response.getRetMsg());
        return response;
    }


    public CommonResult quartzSyncDevice() {
        return quartzSyncDevice(Collections.emptyMap());
    }


    public CommonResult quartzSyncDevice(Map<String, String> headers) {
        String path = "/para/device-sync/quartz";
        log.info("调用para-server设备台账同步接口 path={}", path);
        String result = postJsonAndGetResponse(path, Collections.emptyMap(), headers);
        CommonResult response = JSONUtil.toBean(result, new TypeReference<CommonResult>() {}, true);
        log.info("调用para-server设备台账同步接口返回 path={}, retCode={}, retMsg={}", path,
                response == null ? null : response.getRetCode(), response == null ? null : response.getRetMsg());
        return response;
    }


    public RequestLineCodeListResult requestLineCodeList(RequestLineCodeListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestLineCodeList", Collections.emptyMap());
        return JSONUtil.toBean(result, new TypeReference<RequestLineCodeListResult>() {}, true);
    }


    public RequestBuySinlgeTicketMaxNumResult requestBuySinlgeTicketMaxNum() {
        String result = postJsonAndGetResponse("/ci/app/requestBuySinlgeTicketMaxNum", Collections.emptyMap());
        return JSONUtil.toBean(result, new TypeReference<RequestBuySinlgeTicketMaxNumResult>() {}, true);
    }


    public RequestStationCodeListResult requestStationCodeList(RequestStationCodeListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationCodeList", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationCodeListResult>() {}, true);
    }


    public RequestTicketPriceByStationResult requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTicketPriceByStation", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTicketPriceByStationResult>() {}, true);
    }


    public RequestLineStationCodeVersionResult requestLineStationCodeVersion(RequestLineStationCodeVersionReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestLineStationCodeVersion", Collections.emptyMap());
        return JSONUtil.toBean(result, new TypeReference<RequestLineStationCodeVersionResult>() {}, true);
    }


    public RequestStationNameResult requestStationName(RequestStationNameReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationName", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationNameResult>() {}, true);
    }


    public RequestStationLineInfoResult requestStationLineInfo(RequestStationLineInfoReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationLineInfo", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationLineInfoResult>() {}, true);
    }


    public RequestStationNameBatchResult requestStationNameBatch(RequestStationNameBatchReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationNameBatch", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationNameBatchResult>() {}, true);
    }


    public RiskRuleBlacklistSyncRespDTO riskRuleBlacklistSync(Map<String, String> headers) {
        String path = "/internal/risk/rule-blacklist-sync";
        log.info("调用para-server风险规则命中自动加黑接口 path={}", path);
        String result = postJsonAndGetResponse(path, Collections.emptyMap(), headers);
        RiskRuleBlacklistSyncRespDTO response =
                JSONUtil.toBean(result, new TypeReference<RiskRuleBlacklistSyncRespDTO>() {}, true);
        log.info("调用para-server风险规则命中自动加黑接口返回 path={}, candidates={}, added={}, skipped={}, failed={}",
                path, response == null ? null : response.getCandidates(), response == null ? null : response.getAdded(),
                response == null ? null : response.getSkipped(), response == null ? null : response.getFailed());
        return response;
    }
}
