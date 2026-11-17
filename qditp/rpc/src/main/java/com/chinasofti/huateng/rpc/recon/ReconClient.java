package com.chinasofti.huateng.rpc.recon;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.recon.ReconBatchViewDTO;
import com.chinasofti.huateng.model.recon.ReconCreateBatchReqDTO;
import com.chinasofti.huateng.model.recon.ReconFileTypeEnum;
import com.chinasofti.huateng.model.recon.ReconPartReceiptDTO;
import com.chinasofti.huateng.model.recon.ReconSourceCompleteReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


@Service
public class ReconClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(ReconClient.class);

    private static final String BASE_PATH = "/internal/recon";


    public ReconClient(@Value("${service.recon.url:recon-server-service}") String baseUrl,
                       @Value("${service.recon.openLogger:false}") boolean openLogger,
                       WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }


    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofMinutes(5);
    }


    public ReconBatchViewDTO createBatch(ReconCreateBatchReqDTO request) {
        String response = postJsonAndGetResponse(BASE_PATH + "/batches", request, new HashMap<>());
        return parse(response, ReconBatchViewDTO.class, "创建对账批次");
    }


    public CommonResult runDailyBatch() {
        return runDailyBatch(new HashMap<>());
    }


    public CommonResult runDailyBatch(Map<String, String> headers) {
        String response = postJsonAndGetResponse(BASE_PATH + "/daily/run", new HashMap<>(), headers);
        return parse(response, CommonResult.class, "触发日终对账");
    }


    public ReconBatchViewDTO getBatch(String batchId) {
        String response = getAndGetResponse(BASE_PATH + "/batches/" + batchId, new HashMap<>(), new HashMap<>());
        return parse(response, ReconBatchViewDTO.class, "查询对账批次");
    }


    public ReconPartReceiptDTO uploadPart(String batchId, String source, ReconFileTypeEnum fileType, int partNo,
                                          long recordCount, long amountTotal, String sha256, Path file) {
        String uri = BASE_PATH + "/batches/" + batchId + "/sources/" + source
                + "/files/" + fileType.name() + "/parts/" + partNo
                + "?recordCount=" + recordCount + "&amountTotal=" + amountTotal + "&sha256=" + sha256;
        ResponseEntity<String> entity;
        try {
            entity = getProxyHttpClient().post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromResource(new FileSystemResource(file)))
                    .retrieve()
                    .onStatus(status -> status.isError(), resp -> resp.createError())
                    .toEntity(String.class)
                    .block();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("上送对账分片失败 batchId=" + batchId + ", source=" + source
                    + ", fileType=" + fileType + ", partNo=" + partNo + ": " + ex.getMessage(), ex);
        }
        String body = entity == null ? null : entity.getBody();
        log.info("上送对账分片完成 batchId={}, source={}, fileType={}, partNo={}, records={}, amount={}",
                batchId, source, fileType, partNo, recordCount, amountTotal);
        return parse(body, ReconPartReceiptDTO.class, "上送对账分片");
    }


    public void declareComplete(String batchId, String source, ReconFileTypeEnum fileType,
                                ReconSourceCompleteReqDTO request) {
        String uri = BASE_PATH + "/batches/" + batchId + "/sources/" + source
                + "/files/" + fileType.name() + "/complete";
        String response = postJsonAndGetResponse(uri, request, new HashMap<>());
        log.info("声明对账来源完成 batchId={}, source={}, fileType={}, totalParts={}, totalRecords={}, failReason={}",
                batchId, source, fileType, request.getTotalParts(), request.getTotalRecords(), request.getFailReason());
        if (response == null) {
            throw new IllegalStateException("声明对账来源完成无响应 batchId=" + batchId + ", source=" + source);
        }
    }

    private <T> T parse(String response, Class<T> type, String action) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException(action + "无响应");
        }
        T parsed;
        try {
            parsed = JSON.parseObject(response, type);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(action + "响应无法解析: " + response, ex);
        }
        if (parsed == null) {
            throw new IllegalStateException(action + "响应为空对象: " + response);
        }
        return parsed;
    }
}
