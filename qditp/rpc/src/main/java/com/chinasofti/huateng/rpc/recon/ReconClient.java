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
 *
 * <p>分片上传走 {@code application/octet-stream} **流式**发送（{@link BodyInserters#fromResource}），
 * NEVER 改成把文件读成 {@code byte[]} 再发：单片上限 128MB，读成数组等于每片一次 128MB 的堆分配，
 * 并发几片就 OOM。也 NEVER 改成 JSON —— Base64 会把体积放大 33%，且 400 万条明细无法整体入内存。</p>
 *
 * <p>本客户端的方法**失败即抛异常**，不返回 {@code false}。调用方（{@link ReconPartSink}）据此
 * 中断本次抽取并向 recon-server 声明失败，由 recon-server 的补偿任务重试；NEVER 吞异常继续写下一片，
 * 那会产生分片号空洞，最终在生成阶段报「分片序号不连续」而整批失败。</p>
 *
 * <p><b>【开发测试阶段：不再发送 {@code X-Recon-Token}】</b>用户 2026-09-11 要求
 * 「删除令牌要求，不用令牌了，当前处于开发测试阶段」，recon-server 侧已不校验该头，
 * 故这里连带去掉发送，顺带消除 {@code InternalMicroHttp} / {@code FirstFilter} 把请求头 Map
 * 打进日志时的令牌明文泄漏。恢复鉴权时两端 MUST 同时改回。</p>
 */
@Service
public class ReconClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(ReconClient.class);

    private static final String BASE_PATH = "/internal/recon";

    /**
     * 构造 recon-server 客户端。
     *
     * @param baseUrl          recon-server 基础地址，取自配置 {@code service.recon.url}
     * @param openLogger       是否打印请求 / 响应报文；**默认 false**，开启后明细 JSON 会灌满日志
     * @param webClientBuilder Spring 注入的 WebClient 构造器
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
     *
     * @param request 批次请求
     * @return 批次视图
     */
    public ReconBatchViewDTO createBatch(ReconCreateBatchReqDTO request) {
        String response = postJsonAndGetResponse(BASE_PATH + "/batches", request, new HashMap<>());
        return parse(response, ReconBatchViewDTO.class, "创建对账批次");
    }

    /**
     * 触发一次完整的日终对账，供 web-admin 的 Quartz 任务 {@code reconQuartzTask.runDailyBatch()} 调用。
     *
     * <p>recon-server 侧**同步跑完**才返回，因此本调用会阻塞到批次收口或超时
     * （服务端 {@code recon.orchestration.run-timeout-millis} 默认 4 分钟，小于本客户端
     * {@link #getResponseTimeout()} 的 5 分钟）。</p>
     *
     * @return {@code retCode=0000} 表示对账完成；其它值表示失败，调用方 MUST 据此抛异常
     */
    public CommonResult runDailyBatch() {
        return runDailyBatch(new HashMap<>());
    }

    /**
     * 带请求头的重载。
     *
     * <p>供 Quartz 任务传 trace 头用：{@code ProxyWebClient} 只自动透传 MDC 里的 {@code authorization}、
     * 不注入任何 trace 头，不传就在调度日志里断链（{@code docs/architecture/web-server.md} §7.6）。
     * 无参重载委托到本方法传空 Map，**NEVER 让两个重载各写一份请求逻辑**。</p>
     *
     * @param headers 附加请求头
     */
    public CommonResult runDailyBatch(Map<String, String> headers) {
        String response = postJsonAndGetResponse(BASE_PATH + "/daily/run", new HashMap<>(), headers);
        return parse(response, CommonResult.class, "触发日终对账");
    }

    /**
     * 查询批次当前状态。
     *
     * @param batchId 批次标识
     * @return 批次视图
     */
    public ReconBatchViewDTO getBatch(String batchId) {
        String response = getAndGetResponse(BASE_PATH + "/batches/" + batchId, new HashMap<>(), new HashMap<>());
        return parse(response, ReconBatchViewDTO.class, "查询对账批次");
    }

    /**
     * 上送一个分片文件。
     *
     * <p>服务端按 {@code (batchId, source, fileType, partNo)} 幂等：哈希相同直接返回首次回执，
     * 哈希不同拒收。因此重试 MUST 用同一 partNo 与同一文件内容。</p>
     *
     * @param batchId     批次标识
     * @param source      来源标识，与 recon-server 期望清单里的名字一致
     * @param fileType    文件类型
     * @param partNo      分片号，从 0 开始连续编号
     * @param recordCount 本片行数
     * @param amountTotal 本片金额合计，单位分
     * @param sha256      本片内容的 SHA-256 十六进制小写
     * @param file        本地分片文件
     * @return 接收回执
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
     *
     * @param batchId  批次标识
     * @param source   来源标识
     * @param fileType 文件类型
     * @param request  声明内容，含分片总数、记录总数、金额合计或失败原因
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
