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

/**
 * recon-server 的内部客户端，供四个源服务上送对账分片与声明收齐。
 */
@Service
public class ReconClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(ReconClient.class);

    private static final String BASE_PATH = "/internal/recon";

    /**
     * 构造 recon-server 客户端。
     * @param baseUrl recon-server 基础地址，取自配置 {@code service.recon.url}
     * @param openLogger 是否打印请求 / 响应报文；**默认 false**，开启后明细 JSON 会灌满日志。
     * @param webClientBuilder Spring 注入的 WebClient 构造器。
     */
    public ReconClient(@Value("${service.recon.url:recon-server-service}") String baseUrl,
                       @Value("${service.recon.openLogger:false}") boolean openLogger,
                       WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 分片上传可能传输上百 MB，响应超时放到 5 分钟；默认的 10 秒会在大分片上稳定超时。
     */
    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofMinutes(5);
    }

    /**
     * 创建对账批次，按 batchId 幂等。
     * @param request 批次请求。
     * @return 批次视图。
     */
    public ReconBatchViewDTO createBatch(ReconCreateBatchReqDTO request) {
        String response = postJsonAndGetResponse(BASE_PATH + "/batches", request, new HashMap<>());
        return parse(response, ReconBatchViewDTO.class, "创建对账批次");
    }

    /**
     * 触发一次完整的日终对账，供 web-admin 的 Quartz 任务 {@code reconQuartzTask.runDailyBatch()} 调用。
     * @return {@code retCode=0000} 表示对账完成；其它值表示失败，调用方。
     */
    public CommonResult runDailyBatch() {
        return runDailyBatch(new HashMap<>());
    }

    /**
     * 带请求头的重载。
     * @param headers 附加请求头。
     */
    public CommonResult runDailyBatch(Map<String, String> headers) {
        String response = postJsonAndGetResponse(BASE_PATH + "/daily/run", new HashMap<>(), headers);
        return parse(response, CommonResult.class, "触发日终对账");
    }

    /**
     * 查询批次当前状态。
     * @param batchId 批次标识。
     * @return 批次视图。
     */
    public ReconBatchViewDTO getBatch(String batchId) {
        String response = getAndGetResponse(BASE_PATH + "/batches/" + batchId, new HashMap<>(), new HashMap<>());
        return parse(response, ReconBatchViewDTO.class, "查询对账批次");
    }

    /**
     * 上送一个分片文件。
     * @param batchId 批次标识。
     * @param source 来源标识，与 recon-server 期望清单里的名字一致。
     * @param fileType 文件类型。
     * @param partNo 分片号，从 0 开始连续编号。
     * @param recordCount 本片行数。
     * @param amountTotal 本片金额合计，单位分。
     * @param sha256 本片内容的 SHA-256 十六进制小写。
     * @param file 本地分片文件。
     * @return 接收回执。
     */
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

    /**
     * 声明某个 {@code (批次, 来源, 文件类型)} 的分片已全部上送完毕，或本次抽取失败。
     * @param batchId 批次标识。
     * @param source 来源标识。
     * @param fileType 文件类型。
     * @param request 声明内容，含分片总数、记录总数、金额合计或失败。
     */
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
